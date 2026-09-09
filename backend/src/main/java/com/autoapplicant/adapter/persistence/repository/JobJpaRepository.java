package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    @Query("SELECT j FROM JobEntity j WHERE j.isActive = true AND j.id NOT IN :excludedIds ORDER BY j.postedAt DESC")
    List<JobEntity> findAllExcluding(@Param("excludedIds") Set<UUID> excludedIds, Pageable pageable);

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
