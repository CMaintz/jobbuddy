package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.port.in.notification.GetNudgesUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NudgeService implements GetNudgesUseCase {

    private static final int DEADLINE_WINDOW_DAYS = 7;
    private static final int FOLLOW_UP_AFTER_DAYS = 14;

    private final ApplicationRepositoryPort applicationRepo;
    private final JobRepositoryPort jobRepo;
    private final FollowUpReminderRepositoryPort reminderRepo;
    private final OutreachContactRepositoryPort outreachRepo;
    private final Clock clock;

    public NudgeService(ApplicationRepositoryPort applicationRepo, JobRepositoryPort jobRepo,
                        FollowUpReminderRepositoryPort reminderRepo,
                        OutreachContactRepositoryPort outreachRepo,
                        Clock clock) {
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.reminderRepo = reminderRepo;
        this.outreachRepo = outreachRepo;
        this.clock = clock;
    }

    @Override
    public List<Nudge> getNudges(UUID userId) {
        List<Application> apps = applicationRepo.findByUserId(userId);
        Map<UUID, Job> jobs = jobRepo.findByIds(apps.stream().map(Application::jobId).distinct().toList())
                .stream().collect(Collectors.toMap(Job::id, Function.identity()));

        LocalDate today = LocalDate.now(clock);
        List<Nudge> deadlineNudges = new ArrayList<>();
        List<Nudge> followUpNudges = new ArrayList<>();

        for (Application app : apps) {
            Job job = jobs.get(app.jobId());
            if (job == null) continue;

            if ((app.status() == ApplicationStatus.SAVED || app.status() == ApplicationStatus.PREPARING)
                    && job.applicationDeadline() != null
                    && !job.applicationDeadline().isBefore(today)
                    && !job.applicationDeadline().isAfter(today.plusDays(DEADLINE_WINDOW_DAYS))) {
                deadlineNudges.add(Nudge.forApplication(Nudge.NudgeType.DEADLINE_SOON, app.id(),
                        job.id(), job.title(), job.companyName(), job.applicationDeadline(), null));
            }

            if (app.status() == ApplicationStatus.APPLIED
                    && app.appliedAt() != null
                    && (app.recruiterReply() == null || app.recruiterReply().isBlank())) {
                long days = ChronoUnit.DAYS.between(app.appliedAt(), Instant.now());
                if (days >= FOLLOW_UP_AFTER_DAYS) {
                    followUpNudges.add(Nudge.forApplication(Nudge.NudgeType.FOLLOW_UP, app.id(),
                            job.id(), job.title(), job.companyName(), null, (int) days));
                }
            }
        }

        deadlineNudges.sort(Comparator.comparing(Nudge::deadline));
        followUpNudges.sort(Comparator.comparing(Nudge::daysSinceApplied).reversed());

        // Things the user explicitly asked to be reminded of come first: they chose the date, so
        // it outranks anything the app inferred.
        List<Nudge> result = new ArrayList<>(dueReminders(userId));
        result.addAll(dueOutreach(userId, today));
        result.addAll(deadlineNudges);
        result.addAll(followUpNudges);
        return result;
    }

    /**
     * Reminders the user set on an application that have come due.
     *
     * <p>These existed and were reachable only by opening the application they belong to, which is
     * the one place someone who has forgotten about it will not look.
     */
    private List<Nudge> dueReminders(UUID userId) {
        // Through the end of today, not "as of this second": something due at 16:00 is due today,
        // and a morning email that omits it is wrong by the time anyone reads it.
        Instant endOfToday = LocalDate.now(clock).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
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
