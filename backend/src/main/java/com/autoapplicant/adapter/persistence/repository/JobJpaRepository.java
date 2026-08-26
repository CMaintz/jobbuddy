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

    @Query("SELECT j FROM JobEntity j WHERE j.aiSummary IS NULL ORDER BY j.createdAt ASC")
    List<JobEntity> findUnenriched(Pageable pageable);

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
}
