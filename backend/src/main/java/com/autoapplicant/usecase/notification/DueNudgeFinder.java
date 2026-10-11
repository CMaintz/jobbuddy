package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The nudges whose date the user chose themselves and that have arrived: reminders set on an
 * application, and tracked outreach whose follow-up date is here. Shared by the in-app nudge feed
 * and the daily email, which mails only these.
 */
@Component
public class DueNudgeFinder {

    private final ApplicationRepositoryPort applicationRepo;
    private final JobRepositoryPort jobRepo;
    private final FollowUpReminderRepositoryPort reminderRepo;
    private final OutreachContactRepositoryPort outreachRepo;
    private final Clock clock;

    public DueNudgeFinder(ApplicationRepositoryPort applicationRepo, JobRepositoryPort jobRepo,
                          FollowUpReminderRepositoryPort reminderRepo,
                          OutreachContactRepositoryPort outreachRepo,
                          Clock clock) {
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.reminderRepo = reminderRepo;
        this.outreachRepo = outreachRepo;
        this.clock = clock;
    }

    /** Due reminders first, then due outreach follow-ups, each soonest first. */
    public List<Nudge> findDueToday(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        List<Nudge> due = new ArrayList<>(dueReminders(userId, today));
        due.addAll(dueOutreach(userId, today));
        return due;
    }

    /**
     * Reminders the user set on an application that have come due.
     *
     * <p>These existed and were reachable only by opening the application they belong to, which is
     * the one place someone who has forgotten about it will not look.
     */
    private List<Nudge> dueReminders(UUID userId, LocalDate today) {
        // Through the end of today, not "as of this second": something due at 16:00 is due today,
        // and a morning email that omits it is wrong by the time anyone reads it.
        Instant endOfToday = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return reminderRepo.findDueByUserId(userId, endOfToday).stream()
                .filter(reminder -> !reminder.completed())
                .map(reminder -> {
                    Application app = reminder.applicationId() != null
                            ? applicationRepo.findByIdAndUserId(reminder.applicationId(), userId).orElse(null)
                            : null;
                    Job job = app != null ? jobRepo.findById(app.jobId()).orElse(null) : null;
                    return new Nudge(Nudge.NudgeType.REMINDER_DUE,
                            reminder.applicationId(), job != null ? job.id() : null, null, reminder.id(),
                            job != null ? job.title() : null, job != null ? job.companyName() : null,
                            LocalDate.ofInstant(reminder.dueAt(), ZoneOffset.UTC), null, reminder.note());
                })
                .sorted(Comparator.comparing(Nudge::deadline))
                .toList();
    }

    /** Tracked unsolicited outreach that has reached its follow-up date and is still open. */
    private List<Nudge> dueOutreach(UUID userId, LocalDate today) {
        return outreachRepo.findByUserId(userId).stream()
                .filter(contact -> contact.isFollowUpDue(today))
                .map(contact -> new Nudge(Nudge.NudgeType.OUTREACH_FOLLOW_UP, null, null,
                        contact.id(), null, null, contact.companyName(),
                        contact.followUpDue(), null, contact.contactName()))
                .sorted(Comparator.comparing(Nudge::deadline))
                .toList();
    }
}
