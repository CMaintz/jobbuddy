package com.autoapplicant.port.out.reminder;

import com.autoapplicant.domain.reminder.FollowUpReminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpReminderRepositoryPort {
    List<FollowUpReminder> findByApplicationIdAndUserId(UUID applicationId, UUID userId);
    List<FollowUpReminder> findDueByUserId(UUID userId, Instant upTo);
    Optional<FollowUpReminder> findByIdAndUserId(UUID id, UUID userId);
    FollowUpReminder save(FollowUpReminder reminder);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
