package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.SavedJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedJobJpaRepository extends JpaRepository<SavedJobEntity, UUID> {
    List<SavedJobEntity> findByUserId(UUID userId);
    Optional<SavedJobEntity> findByUserIdAndJobId(UUID userId, UUID jobId);
    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

    @Query("SELECT s.jobId FROM SavedJobEntity s WHERE s.userId = :userId")
    List<UUID> findJobIdsByUserId(UUID userId);

    void deleteByUserIdAndJobId(UUID userId, UUID jobId);
}
