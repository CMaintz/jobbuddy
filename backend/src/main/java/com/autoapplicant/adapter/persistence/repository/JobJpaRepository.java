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
     * skipping any that have been given up on or retried too recently.
     */
    @Query("""
            SELECT j FROM JobEntity j
            WHERE j.enrichmentStatus = 'PENDING'
              AND j.enrichmentAttempts < :maxAttempts
              AND (j.enrichmentLastAttemptAt IS NULL OR j.enrichmentLastAttemptAt < :retryBefore)
            ORDER BY j.enrichmentLastAttemptAt ASC NULLS FIRST, j.createdAt ASC
            """)
    List<JobEntity> findForEnrichment(@Param("maxAttempts") int maxAttempts,
                                      @Param("retryBefore") Instant retryBefore,
                                      Pageable pageable);

    @Query("SELECT j.enrichmentStatus, count(j) FROM JobEntity j GROUP BY j.enrichmentStatus")
    List<Object[]> countByEnrichmentStatus();

    /**
     * Active postings the user has not ignored. A correlated NOT EXISTS rather than binding the
     * user's ignored ids into NOT IN, so the query does not grow with how much they have ignored.
     * The page query and the count share it, so the total always matches what the pages hold.
     */
    String ACTIVE_NOT_IGNORED = " FROM JobEntity j WHERE j.isActive = true AND NOT EXISTS ("
            + "SELECT 1 FROM IgnoredJobEntity i WHERE i.userId = :userId AND i.jobId = j.id)";

    @Query("SELECT j" + ACTIVE_NOT_IGNORED + " ORDER BY j.postedAt DESC")
    List<JobEntity> findActiveNotIgnoredBy(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT count(j)" + ACTIVE_NOT_IGNORED)
    long countActiveNotIgnoredBy(@Param("userId") UUID userId);

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
     * text; without it, a run that never enriches can still embed what ingest stored.
     */
    @Query("""
            SELECT j FROM JobEntity j
            WHERE j.embeddingStatus = 'PENDING'
              AND (:requireEnriched = false OR j.enrichmentStatus = 'ENRICHED')
              AND j.embeddingAttempts < :maxAttempts
              AND (j.embeddingLastAttemptAt IS NULL OR j.embeddingLastAttemptAt < :retryBefore)
            ORDER BY j.embeddingLastAttemptAt ASC NULLS FIRST, j.createdAt ASC
            """)
    List<JobEntity> findForEmbedding(@Param("maxAttempts") int maxAttempts,
                                     @Param("retryBefore") Instant retryBefore,
                                     @Param("requireEnriched") boolean requireEnriched,
                                     Pageable pageable);

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
}
