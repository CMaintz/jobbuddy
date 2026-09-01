package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ApplicationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationJpaRepository extends JpaRepository<ApplicationEntity, UUID> {
    List<ApplicationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<ApplicationEntity> findByUserId(UUID userId, Pageable pageable);
    Optional<ApplicationEntity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT a.jobId FROM ApplicationEntity a WHERE a.userId = :userId AND a.jobId IS NOT NULL")
    java.util.List<UUID> findAppliedJobIds(@org.springframework.data.repository.query.Param("userId") UUID userId);

    @Query("""
            select a.outcomeLessons from ApplicationEntity a
            where a.userId = :userId and a.outcomeLessons is not null and a.outcomeLessons <> ''
            order by a.updatedAt desc""")
    List<String> findRecentOutcomeLessons(UUID userId, Pageable pageable);
}
