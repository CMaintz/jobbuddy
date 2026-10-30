package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.port.in.notification.GetNudgesUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NudgeService implements GetNudgesUseCase {

    private static final int DEADLINE_WINDOW_DAYS = 7;
    private static final int FOLLOW_UP_AFTER_DAYS = 14;

    private final ApplicationRepositoryPort applicationRepo;
    private final JobRepositoryPort jobRepo;
    private final DueNudgeFinder dueNudges;
    private final Clock clock;

    public NudgeService(ApplicationRepositoryPort applicationRepo, JobRepositoryPort jobRepo,
                        DueNudgeFinder dueNudges, Clock clock) {
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.dueNudges = dueNudges;
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
                long days = ChronoUnit.DAYS.between(app.appliedAt(), clock.instant());
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
        List<Nudge> result = new ArrayList<>(dueNudges.findDueToday(userId));
        result.addAll(deadlineNudges);
        result.addAll(followUpNudges);
        return result;
    }
}
