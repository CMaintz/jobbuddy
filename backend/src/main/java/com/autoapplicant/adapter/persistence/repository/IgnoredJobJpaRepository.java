package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.IgnoredJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface IgnoredJobJpaRepository extends JpaRepository<IgnoredJobEntity, UUID> {
    List<IgnoredJobEntity> findByUserIdOrderByIgnoredAtDesc(UUID userId);
    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);
    void deleteByUserIdAndJobId(UUID userId, UUID jobId);

    @Query("SELECT e.jobId FROM IgnoredJobEntity e WHERE e.userId = :userId")
    List<UUID> findJobIdsByUserId(UUID userId);
}
