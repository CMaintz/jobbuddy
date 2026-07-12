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
}
