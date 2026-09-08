package com.autoapplicant.adapter.notification;

import com.autoapplicant.port.in.notification.SendDueRemindersUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DueReminderScheduler {

    private final SendDueRemindersUseCase dueReminders;

    public DueReminderScheduler(SendDueRemindersUseCase dueReminders) {
        this.dueReminders = dueReminders;
    }

    /** Early enough to act on the same working day, late enough not to arrive overnight. */
    @Scheduled(cron = "${app.reminders.cron:0 0 7 * * *}")
    public void run() {
        dueReminders.sendDueReminders();
    }
}
