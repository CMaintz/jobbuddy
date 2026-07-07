package com.autoapplicant.usecase.reminder;

import com.autoapplicant.domain.reminder.FollowUpReminder;
import com.autoapplicant.port.in.reminder.ManageFollowUpRemindersUseCase;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class FollowUpReminderService implements ManageFollowUpRemindersUseCase {

    private final FollowUpReminderRepositoryPort repo;

    public FollowUpReminderService(FollowUpReminderRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<FollowUpReminder> getForApplication(UUID applicationId, UUID userId) {
        return repo.findByApplicationIdAndUserId(applicationId, userId);
    }

    @Override
    public List<FollowUpReminder> getDueReminders(UUID userId) {
        // Return reminders due within the next 24 hours (overdue + due today)
        return repo.findDueByUserId(userId, Instant.now().plus(24, ChronoUnit.HOURS));
    }

    @Override
    public List<FollowUpReminder> getOpenReminders(UUID userId) {
        return repo.findOpenByUserId(userId);
    }

    @Override
    public FollowUpReminder createReminder(UUID applicationId, UUID userId, String note, Instant dueAt) {
        return repo.save(new FollowUpReminder(null, applicationId, userId, note, dueAt, false, null, null, null));
    }

    @Override
    public FollowUpReminder completeReminder(UUID reminderId, UUID userId) {
        FollowUpReminder existing = repo.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
        return repo.save(new FollowUpReminder(
                existing.id(), existing.applicationId(), existing.userId(),
                existing.note(), existing.dueAt(),
                true, Instant.now(),
                existing.createdAt(), null));
    }

    @Override
    public void deleteReminder(UUID reminderId, UUID userId) {
        repo.deleteByIdAndUserId(reminderId, userId);
    }
}
