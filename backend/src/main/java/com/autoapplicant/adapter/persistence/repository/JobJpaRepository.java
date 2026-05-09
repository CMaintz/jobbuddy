package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JobJpaRepository extends JpaRepository<JobEntity, UUID> {
    Optional<JobEntity> findBySourceAndSourceJobId(String source, String sourceJobId);

    @Query("SELECT j FROM JobEntity j WHERE j.isActive = true ORDER BY j.postedAt DESC")
    List<JobEntity> findActiveJobs();

    @Query("SELECT j FROM JobEntity j WHERE j.id NOT IN :excludedIds ORDER BY j.postedAt DESC")
    List<JobEntity> findAllExcluding(@Param("excludedIds") Set<UUID> excludedIds, Pageable pageable);
}
