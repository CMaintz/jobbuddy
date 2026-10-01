package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ApplicationStatusEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationStatusEventJpaRepository extends JpaRepository<ApplicationStatusEventEntity, UUID> {
    List<ApplicationStatusEventEntity> findByUserIdOrderByOccurredAtAsc(UUID userId);

    List<ApplicationStatusEventEntity> findByApplicationIdAndUserIdOrderByOccurredAtAsc(UUID applicationId, UUID userId);

    @Query("SELECT e.occurredAt FROM ApplicationStatusEventEntity e WHERE e.userId = :userId AND e.occurredAt >= :since")
    List<Instant> findOccurredAtSince(UUID userId, Instant since);
}
