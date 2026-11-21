package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.notification.UpcomingDate;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.reminder.FollowUpReminderRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The nudges whose date the user chose themselves and that have arrived: reminders set on an
 * application, and tracked outreach whose follow-up date is here. Shared by the in-app nudge feed
 * and the daily email, which mails only these.
 */
@Component
public class DueNudgeFinder {

    /**
     * How far ahead the user's own deadline is mailed: close enough to act on, short enough that
     * one deadline is not repeated for a week of mornings.
     */
    private static final int DEADLINE_DAYS_AHEAD = 2;

    /** Today's and tomorrow's interviews: tomorrow's is the one there is still time to prepare. */
    private static final int INTERVIEW_DAYS_AHEAD = 1;

    /** Once the application has gone out, its deadline no longer asks anything of the user. */
    private static final Set<ApplicationStatus> NOT_YET_SENT =
            Set.of(ApplicationStatus.SAVED, ApplicationStatus.PREPARING);

    private static final Set<ApplicationStatus> CLOSED =
            Set.of(ApplicationStatus.REJECTED, ApplicationStatus.ARCHIVED);

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
     * The user's own deadlines and interviews coming up soon, soonest first. Unlike the crawled
     * posting's deadline these are dates the user set, so they are worth a mail.
     */
    public List<UpcomingDate> findUpcoming(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        List<Application> apps = applicationRepo.findByUserId(userId);
        List<Application> withDeadline = apps.stream()
                .filter(app -> NOT_YET_SENT.contains(app.status()))
                .filter(app -> within(app.deadline(), today, DEADLINE_DAYS_AHEAD))
                .toList();
        List<Application> withInterview = apps.stream()
                .filter(app -> !CLOSED.contains(app.status()))
                .filter(app -> app.interviewAt() != null
                        && within(app.interviewAt().toLocalDate(), today, INTERVIEW_DAYS_AHEAD))
                .toList();
        Map<UUID, Job> jobs = jobsOf(Stream.concat(withDeadline.stream(), withInterview.stream()).toList());

        List<UpcomingDate> upcoming = new ArrayList<>();
        withDeadline.forEach(app -> upcoming.add(upcomingDate(UpcomingDate.Kind.DEADLINE, app,
                jobs.get(app.jobId()), app.deadline(), null)));
        withInterview.forEach(app -> upcoming.add(upcomingDate(UpcomingDate.Kind.INTERVIEW, app,
                jobs.get(app.jobId()), app.interviewAt().toLocalDate(), app.interviewAt().toLocalTime())));
        upcoming.sort(Comparator.comparing(UpcomingDate::date)
                .thenComparing(UpcomingDate::time, Comparator.nullsFirst(Comparator.naturalOrder())));
        return upcoming;
    }

    private static boolean within(LocalDate date, LocalDate today, int daysAhead) {
        return date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(daysAhead));
    }

    private Map<UUID, Job> jobsOf(List<Application> apps) {
        if (apps.isEmpty()) return Map.of();
        List<UUID> ids = apps.stream().map(Application::jobId).distinct().toList();
        return jobRepo.findByIds(ids).stream().collect(Collectors.toMap(Job::id, Function.identity()));
    }

    private static UpcomingDate upcomingDate(UpcomingDate.Kind kind, Application app, Job job,
                                             LocalDate date, LocalTime time) {
        return new UpcomingDate(kind, app.id(), job != null ? job.title() : null,
                job != null ? job.companyName() : null, date, time);
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
                            LocalDate.ofInstant(reminder.dueAt(), ZoneOffset.UTC), null, reminder.note(), null);
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
                        contact.followUpDue(), null, contact.contactName(), null))
                .sorted(Comparator.comparing(Nudge::deadline))
                .toList();
    }
}
