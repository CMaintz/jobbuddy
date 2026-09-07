package com.autoapplicant.port.in.reminder;

import com.autoapplicant.domain.reminder.FollowUpReminder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ManageFollowUpRemindersUseCase {
    List<FollowUpReminder> getForApplication(UUID applicationId, UUID userId);
    List<FollowUpReminder> getDueReminders(UUID userId);
    FollowUpReminder createReminder(UUID applicationId, UUID userId, String note, Instant dueAt);
    FollowUpReminder completeReminder(UUID reminderId, UUID userId);
    void deleteReminder(UUID reminderId, UUID userId);
}
