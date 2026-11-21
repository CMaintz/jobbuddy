package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobJpaRepository extends JpaRepository<JobEntity, UUID> {
    Optional<JobEntity> findBySourceAndSourceJobId(String source, String sourceJobId);
    boolean existsBySourceAndSourceJobId(String source, String sourceJobId);

    Optional<JobEntity> findByUrl(String url);

    @Query("SELECT j FROM JobEntity j WHERE j.isActive = true ORDER BY j.postedAt DESC")
    List<JobEntity> findActiveJobs();

    @Query("SELECT j FROM JobEntity j WHERE j.isActive = true ORDER BY j.postedAt DESC")
    List<JobEntity> findActiveJobs(Pageable pageable);

    /** Live postings sort ahead of closed ones, newest first within each group. */
    @Query("SELECT j FROM JobEntity j WHERE j.companyId = :companyId ORDER BY j.isActive DESC, j.postedAt DESC")
    List<JobEntity> findByCompanyId(@Param("companyId") UUID companyId, Pageable pageable);

    /**
     * The enrichment queue: never-attempted jobs first, then the ones tried longest ago,
     * skipping any that have been given up on or retried too recently. Only postings a user
     * could still act on: live, not past their deadline, not ignored, applied to or closed by
     * every user (SAVED and PREPARING are leads still being worked on, so they stay in), not in
     * a category everyone skips, and within one of the areas. "Every user" is every user with
     * saved preferences, or every user when none has saved any, the same rule as the skip list:
     * one user's ignore must not take a posting away from another. A posting with no known place,
     * or a remote or hybrid one (empty places), is always in, and so is one she chose herself
     * (saved, being prepared, or added by hand). A posting not placed yet (NULL) waits until
     * it is, rather than passing as "anywhere" while a backlog is placed slice by slice.
     *
     * <p>Native for the distance test over the place arrays. The areas arrive as three parallel
     * array literals ({@code "{lat,...}"}); {@code anywhere} switches the area test off.
     */
    @Query(value = """
            SELECT j.* FROM jobs j
            WHERE j.enrichment_status = 'PENDING'
              AND j.enrichment_attempts < :maxAttempts
              AND (j.enrichment_last_attempt_at IS NULL OR j.enrichment_last_attempt_at < :retryBefore)
              AND j.is_active = true
              AND (j.application_deadline IS NULL OR j.application_deadline >= :today)
              AND (NOT EXISTS (SELECT 1 FROM users) OR EXISTS (
                    SELECT 1 FROM users u
                    WHERE (u.id IN (SELECT p.user_id FROM preferences p)
                           OR NOT EXISTS (SELECT 1 FROM preferences))
                      AND NOT EXISTS (SELECT 1 FROM ignored_jobs i
                                      WHERE i.job_id = j.id AND i.user_id = u.id)
                      AND NOT EXISTS (SELECT 1 FROM applications a
                                      WHERE a.job_id = j.id AND a.user_id = u.id
                                        AND a.status NOT IN ('SAVED', 'PREPARING'))))
              AND (j.job_category IS NULL OR j.job_category NOT IN (:excludedCategories))
              AND (:anywhere OR cardinality(j.place_lats) = 0 OR j.source = 'MANUAL'
                   OR EXISTS (SELECT 1 FROM applications s
                              WHERE s.job_id = j.id AND s.status IN ('SAVED', 'PREPARING'))
                   OR EXISTS (
                    SELECT 1
                    FROM unnest(j.place_lats, j.place_lons) AS p(lat, lon),
                         unnest(CAST(:areaLats AS double precision[]), CAST(:areaLons AS double precision[]),
                                CAST(:areaKms AS double precision[])) AS h(lat, lon, km)
                    WHERE 2 * 6371 * asin(sqrt(
                            power(sin(radians(p.lat - h.lat) / 2), 2)
                            + cos(radians(h.lat)) * cos(radians(p.lat))
                              * power(sin(radians(p.lon - h.lon) / 2), 2))) <= h.km))
            ORDER BY j.enrichment_last_attempt_at ASC NULLS FIRST, j.created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<JobEntity> findForEnrichment(@Param("maxAttempts") int maxAttempts,
                                      @Param("retryBefore") Instant retryBefore,
                                      @Param("today") java.time.LocalDate today,
                                      @Param("excludedCategories") java.util.Collection<String> excludedCategories,
                                      @Param("anywhere") boolean anywhere,
                                      @Param("areaLats") String areaLats,
                                      @Param("areaLons") String areaLons,
                                      @Param("areaKms") String areaKms,
                                      @Param("limit") int limit);

    /** Pending postings whose location has not been placed on the map yet. */
    @Query("SELECT j FROM JobEntity j WHERE j.enrichmentStatus = 'PENDING' AND j.placeLats IS NULL")
    List<JobEntity> findUnplacedPending(Pageable pageable);

    @Query("SELECT j.enrichmentStatus, count(j) FROM JobEntity j GROUP BY j.enrichmentStatus")
    List<Object[]> countByEnrichmentStatus();

    /**
     * Active postings the user has not ignored. A correlated NOT EXISTS rather than binding the
     * user's ignored ids into NOT IN, so the query does not grow with how much they have ignored.
     * The page query and the count share it, so the total always matches what the pages hold.
     */
    String ACTIVE_NOT_IGNORED = " FROM JobEntity j WHERE j.isActive = true AND NOT EXISTS ("
            + "SELECT 1 FROM IgnoredJobEntity i WHERE i.userId = :userId AND i.jobId = j.id)"
            + " AND (j.jobCategory IS NULL OR j.jobCategory NOT IN :excludedCategories)";

    @Query("SELECT j" + ACTIVE_NOT_IGNORED + " ORDER BY j.postedAt DESC")
    List<JobEntity> findActiveNotIgnoredBy(@Param("userId") UUID userId,
                                           @Param("excludedCategories") java.util.Collection<String> excludedCategories,
                                           Pageable pageable);

    @Query("SELECT count(j)" + ACTIVE_NOT_IGNORED)
    long countActiveNotIgnoredBy(@Param("userId") UUID userId,
                                 @Param("excludedCategories") java.util.Collection<String> excludedCategories);

    long countByIsActiveTrue();

    /**
     * Postings in the outreach window, newest first. Aggregation happens in the adapter rather
     * than in SQL because the interesting column (technologies) is a text[] that JPQL cannot
     * group over; the Pageable cap keeps that honest on a large jobs table.
     */
    @Query("""
            SELECT j FROM JobEntity j
            WHERE j.companyId IS NOT NULL AND (j.postedAt >= :since OR j.createdAt >= :since)
            ORDER BY j.postedAt DESC NULLS LAST
            """)
    List<JobEntity> findForCompanyAggregation(@Param("since") Instant since, Pageable pageable);

    // Manual jobs are excluded: no crawler ever refreshes their lastSeenAt, so staleness means nothing for them.
    @Query("SELECT j.id FROM JobEntity j WHERE j.isActive = true AND j.lastSeenAt < :cutoff AND j.source <> 'MANUAL'")
    List<UUID> findStaleActiveJobIds(@Param("cutoff") Instant cutoff);

    /**
     * The embedding queue: postings without a vector, never-attempted first. With
     * {@code requireEnriched} only enriched ones, because enrichment trims boilerplate from the
     * text; without it, a run that never enriches can still embed what ingest stored. Live,
     * not-yet-expired postings only: no search returns the others.
     */
    @Query("""
            SELECT j FROM JobEntity j
            WHERE j.embeddingStatus = 'PENDING'
              AND j.isActive = true
              AND (j.applicationDeadline IS NULL OR j.applicationDeadline >= :today)
              AND (:requireEnriched = false OR j.enrichmentStatus = 'ENRICHED')
              AND j.embeddingAttempts < :maxAttempts
              AND (j.embeddingLastAttemptAt IS NULL OR j.embeddingLastAttemptAt < :retryBefore)
            ORDER BY j.embeddingLastAttemptAt ASC NULLS FIRST, j.createdAt ASC
            """)
    List<JobEntity> findForEmbedding(@Param("maxAttempts") int maxAttempts,
                                     @Param("retryBefore") Instant retryBefore,
                                     @Param("requireEnriched") boolean requireEnriched,
                                     @Param("today") java.time.LocalDate today,
                                     Pageable pageable);

    /**
     * Active postings counted as embedded that have no vector for {@code model} go back in the
     * queue with fresh attempts. Other models' vectors are left in place. A posting given up on
     * keeps its status and count, and an expired one is not worth a new vector.
     */
    @Modifying
    @Query(value = """
            UPDATE jobs SET embedding_status = 'PENDING',
                            embedding_attempts = 0,
                            embedding_last_attempt_at = NULL,
                            embedding_last_error = NULL
            WHERE is_active = true
              AND embedding_status = 'EMBEDDED'
              AND NOT EXISTS (SELECT 1 FROM job_embeddings e
                              WHERE e.job_id = jobs.id AND e.model = :model)
            """, nativeQuery = true)
    int requeueEmbeddingsForModel(@Param("model") String model);

    @Query("SELECT j.embeddingStatus, COUNT(j) FROM JobEntity j GROUP BY j.embeddingStatus")
    List<Object[]> countByEmbeddingStatus();

    /**
     * Marks a posting embedded only if its text is still the text the vector was made from, in
     * one statement, so a save that changed the text in between (and re-queued the posting)
     * cannot be overwritten. The expression must match {@code Job.embeddingText()}. Clears the
     * persistence context, since entities loaded before it still hold the old status.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE JobEntity j
            SET j.embeddingStatus = 'EMBEDDED',
                j.embeddingAttempts = j.embeddingAttempts + 1,
                j.embeddingLastAttemptAt = :now,
                j.embeddingLastError = NULL
            WHERE j.id = :id
              AND CONCAT(COALESCE(j.title, ''), ' ', COALESCE(j.descriptionClean, '')) = :embeddedText
            """)
    int markEmbeddedIfTextUnchanged(@Param("id") UUID id,
                                    @Param("embeddedText") String embeddedText,
                                    @Param("now") Instant now);

    /**
     * Enrichment's own columns only. A whole-row save from a job read before the AI call would
     * revert what the crawl wrote in the meantime (is_active, last_seen_at).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE JobEntity j
            SET j.enrichmentStatus = 'ENRICHED',
                j.enrichmentAttempts = j.enrichmentAttempts + 1,
                j.enrichmentLastAttemptAt = :now,
                j.enrichmentLastError = NULL
            WHERE j.id = :id
            """)
    int markEnriched(@Param("id") UUID id, @Param("now") Instant now);

    /** As {@link #markEnriched}: counts the attempt and gives up once it reaches the limit. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE JobEntity j
            SET j.enrichmentAttempts = j.enrichmentAttempts + 1,
                j.enrichmentLastAttemptAt = :now,
                j.enrichmentLastError = :reason,
                j.enrichmentStatus = CASE WHEN j.enrichmentAttempts + 1 >= :maxAttempts
                                          THEN 'FAILED' ELSE 'PENDING' END
            WHERE j.id = :id
            """)
    int markEnrichmentFailed(@Param("id") UUID id, @Param("reason") String reason,
                             @Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    @Query("SELECT j.enrichmentStatus FROM JobEntity j WHERE j.id = :id")
    String findEnrichmentStatus(@Param("id") UUID id);

    /** Seen again by a connector that skipped re-emitting it — liveness only, nothing else. */
    @Modifying
    @Query("UPDATE JobEntity j SET j.lastSeenAt = :lastSeenAt "
         + "WHERE j.source = :source AND j.sourceJobId = :sourceJobId")
    int markSeenBySourceJobId(@Param("source") String source,
                              @Param("sourceJobId") String sourceJobId,
                              @Param("lastSeenAt") Instant lastSeenAt);

    @Modifying
    @Query("UPDATE JobEntity j SET j.isActive = false WHERE j.isActive = true AND j.lastSeenAt < :cutoff AND j.source <> 'MANUAL'")
    int deactivateStaleJobs(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT j FROM JobEntity j
            WHERE j.isActive = true AND j.url IS NOT NULL AND j.source <> 'MANUAL'
              AND (j.lastUrlCheckAt IS NULL OR j.lastUrlCheckAt < :recheckCutoff)
            ORDER BY j.lastUrlCheckAt ASC NULLS FIRST
            """)
    List<JobEntity> findUrlCheckCandidates(@Param("recheckCutoff") Instant recheckCutoff, Pageable pageable);

    @Query("SELECT j.id FROM JobEntity j WHERE j.isActive = true AND j.applicationDeadline < :before")
    List<UUID> findActiveWithDeadlineBefore(@Param("before") java.time.LocalDate before);

    @Query("""
            SELECT j FROM JobEntity j
            WHERE j.contentFingerprint = :fp AND j.isActive = true AND j.id <> :excludeId
            ORDER BY j.createdAt ASC
            """)
    List<JobEntity> findActiveByContentFingerprint(@Param("fp") long fp,
                                                   @Param("excludeId") UUID excludeId,
                                                   Pageable pageable);

    @Modifying
    @Query("UPDATE JobEntity j SET j.contentFingerprint = :fp WHERE j.id = :id")
    void updateContentFingerprint(@Param("id") UUID id, @Param("fp") long fp);

    @Modifying
    @Query("UPDATE JobEntity j SET j.duplicateGroupId = :groupId WHERE j.id = :id")
    void updateDuplicateGroup(@Param("id") UUID id, @Param("groupId") UUID groupId);

    @Modifying
    @Query("UPDATE JobEntity j SET j.isActive = false WHERE j.isActive = true AND j.applicationDeadline < :before")
    int deactivateDeadlineExpired(@Param("before") java.time.LocalDate before);

    /**
     * Every distinct skill label across all postings with the number of postings naming it.
     *
     * <p>Both arrays are unnested and counted together: {@code technologies} and {@code skills}
     * are the same vocabulary filed into two drawers by enrichment, and which drawer a label
     * landed in is reported separately rather than splitting the count. Labels are folded on
     * lower-cased, trimmed text and the most common spelling is returned as the display name —
     * a posting writing "REACT" should not create a second candidate.
     *
     * <p>Rows come back positionally as {@code [label, postings, technologyPostings]}. Nothing
     * in this project boots a Spring context in a test, so a projection interface bound by
     * column name would be verified only in production; positions cannot be misbound.
     */
    @Query(value = """
            SELECT mode() WITHIN GROUP (ORDER BY s.label),
                   COUNT(DISTINCT s.job_id),
                   COUNT(DISTINCT s.job_id) FILTER (WHERE s.from_tech)
            FROM (
                SELECT j.id AS job_id, unnest(j.technologies) AS label, true AS from_tech FROM jobs j
                UNION ALL
                SELECT j.id AS job_id, unnest(j.skills) AS label, false AS from_tech FROM jobs j
            ) s
            WHERE s.label IS NOT NULL AND btrim(s.label) <> ''
            GROUP BY lower(btrim(s.label))
            ORDER BY 2 DESC, 1 ASC
            LIMIT :maxLabels
            """, nativeQuery = true)
    List<Object[]> findSkillMentions(@Param("maxLabels") int maxLabels);

    // Crawl-side writes. Each is one UPDATE of its own columns: the desktop app may be enriching
    // the same posting, and a save of the row read earlier would put back what it replaced.

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JobEntity j SET j.lastSeenAt = :lastSeenAt, j.applicationDeadline = :deadline "
         + "WHERE j.id = :id")
    int refreshLastSeen(@Param("id") UUID id, @Param("lastSeenAt") Instant lastSeenAt,
                        @Param("deadline") java.time.LocalDate deadline);

    /** A live URL also counts as seen, which is what keeps expiry off a posting nobody re-crawls. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JobEntity j SET j.urlCheckFailures = 0, j.lastUrlCheckAt = :now, j.lastSeenAt = :now "
         + "WHERE j.id = :id")
    int markUrlAlive(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JobEntity j SET j.lastUrlCheckAt = :now WHERE j.id = :id")
    int markUrlCheckInconclusive(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JobEntity j SET j.urlCheckFailures = j.urlCheckFailures + 1, j.lastUrlCheckAt = :now "
         + "WHERE j.id = :id")
    int countUrlCheckFailure(@Param("id") UUID id, @Param("now") Instant now);

    /** Deactivates the posting once its failures reach the threshold; 1 when this call did it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JobEntity j SET j.isActive = false "
         + "WHERE j.id = :id AND j.isActive = true AND j.urlCheckFailures >= :threshold")
    int deactivateAtFailureThreshold(@Param("id") UUID id, @Param("threshold") int threshold);

    /** Counts the attempt and gives up once it reaches the limit. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE JobEntity j
            SET j.embeddingAttempts = j.embeddingAttempts + 1,
                j.embeddingLastAttemptAt = :now,
                j.embeddingLastError = :reason,
                j.embeddingStatus = CASE WHEN j.embeddingAttempts + 1 >= :maxAttempts
                                         THEN 'FAILED' ELSE 'PENDING' END
            WHERE j.id = :id
            """)
    int markEmbeddingFailed(@Param("id") UUID id, @Param("reason") String reason,
                            @Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    @Query("SELECT j.embeddingStatus FROM JobEntity j WHERE j.id = :id")
    String findEmbeddingStatus(@Param("id") UUID id);
}
