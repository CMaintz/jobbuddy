package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.FollowUpReminderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpReminderJpaRepository extends JpaRepository<FollowUpReminderEntity, UUID> {
    List<FollowUpReminderEntity> findByApplicationIdAndUserIdOrderByDueAtAsc(UUID applicationId, UUID userId);

    @Query("SELECT r FROM FollowUpReminderEntity r WHERE r.userId = :userId AND r.completed = false AND r.dueAt <= :upTo ORDER BY r.dueAt ASC")
    List<FollowUpReminderEntity> findDueByUserId(@Param("userId") UUID userId, @Param("upTo") Instant upTo);

    Optional<FollowUpReminderEntity> findByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
