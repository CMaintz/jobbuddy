package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.notification.SendWeeklyDigestUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.notification.NotificationPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class WeeklyDigestService implements SendWeeklyDigestUseCase {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestService.class);

    private static final int CANDIDATE_POOL = 200;
    private static final int MAX_JOBS_PER_DIGEST = 8;

    private final PreferencesRepositoryPort preferencesRepo;
    private final UserRepositoryPort userRepo;
    private final JobRepositoryPort jobRepo;
    private final NotificationPort notification;

    public WeeklyDigestService(PreferencesRepositoryPort preferencesRepo,
                               UserRepositoryPort userRepo,
                               JobRepositoryPort jobRepo,
                               NotificationPort notification) {
        this.preferencesRepo = preferencesRepo;
        this.userRepo = userRepo;
        this.jobRepo = jobRepo;
        this.notification = notification;
    }

    @Override
    public int sendDigests() {
        List<UserPreferences> optedIn = preferencesRepo.findAllWithNotificationsEnabled().stream()
                .filter(p -> "WEEKLY".equalsIgnoreCase(p.notificationFrequency()))
                .toList();
        if (optedIn.isEmpty()) return 0;

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Job> recentJobs = jobRepo.findActive(0, CANDIDATE_POOL).stream()
                .filter(j -> newestTimestamp(j).isAfter(weekAgo))
                .toList();
        if (recentJobs.isEmpty()) return 0;

        int sent = 0;
        for (UserPreferences prefs : optedIn) {
            Optional<User> user = userRepo.findById(prefs.userId());
            if (user.isEmpty() || user.get().email() == null) continue;

            List<Job> picks = pickJobs(recentJobs, prefs);
            if (picks.isEmpty()) continue;

            notification.send(user.get().email(),
                    "Your weekly job digest — " + picks.size() + " new matches",
                    composeBody(picks));
            sent++;
        }
        log.info("Weekly digest sent to {} of {} opted-in users", sent, optedIn.size());
        return sent;
    }

    /** Preference-scored picks; falls back to the newest postings when nothing scores. */
    private List<Job> pickJobs(List<Job> jobs, UserPreferences prefs) {
        List<Job> scored = jobs.stream()
                .filter(j -> !matchesAny(j, prefs.negativeSignals()))
                .sorted(Comparator.comparingInt((Job j) -> score(j, prefs)).reversed()
                        .thenComparing(this::newestTimestamp, Comparator.reverseOrder()))
                .toList();

        List<Job> matching = scored.stream().filter(j -> score(j, prefs) > 0).limit(MAX_JOBS_PER_DIGEST).toList();
        return matching.isEmpty() ? scored.stream().limit(5).toList() : matching;
    }

    private int score(Job job, UserPreferences prefs) {
        int score = 0;
        if (matchesAny(job, prefs.positiveSignals())) score += 2;
        if (job.jobCategory() != null && prefs.preferredIndustries() != null
                && prefs.preferredIndustries().contains(job.jobCategory().name())) {
            score += 1;
        }
        return score;
    }

    private boolean matchesAny(Job job, List<String> signals) {
        if (signals == null || signals.isEmpty()) return false;
        String haystack = ((job.title() == null ? "" : job.title()) + " "
                + (job.shortDescription() == null ? "" : job.shortDescription())).toLowerCase(Locale.ROOT);
        return signals.stream().anyMatch(s -> s != null && !s.isBlank()
                && haystack.contains(s.toLowerCase(Locale.ROOT)));
    }

    private Instant newestTimestamp(Job job) {
        if (job.postedAt() != null) return job.postedAt();
        return job.scrapedAt() != null ? job.scrapedAt() : Instant.EPOCH;
    }

    private String composeBody(List<Job> jobs) {
        StringBuilder body = new StringBuilder("New roles from the last week that match your preferences:\n");
        for (Job job : jobs) {
            body.append("\n- ").append(job.title());
            if (job.companyName() != null) body.append(" — ").append(job.companyName());
            if (job.location() != null) body.append(" (").append(job.location()).append(')');
            if (job.applicationDeadline() != null) body.append(" · deadline ").append(job.applicationDeadline());
            if (job.url() != null) body.append("\n  ").append(job.url());
        }
        body.append("\n\nSee all matches in your feed. You can turn this digest off under Settings → Match preferences.");
        return body.toString();
    }
}
