package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.UrlProbeOutcome;
import com.autoapplicant.port.in.job.ReportJobInactiveUseCase;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.JobSearchPort;
import com.autoapplicant.port.out.job.JobUrlProbePort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detects taken-down job postings by probing their URLs.
 *
 * Complements crawl-presence staleness ({@link JobExpiryService}): RSS/API feeds
 * usually only list the newest postings, so "not seen in the feed" is a weak
 * signal on its own. The probe distinguishes "job left the feed but is still up"
 * (probe ALIVE refreshes lastSeenAt) from "job was taken down" (404/410 or an
 * expired-posting page deactivates it).
 *
 * Deactivation rules: a hard 404/410 deactivates immediately; soft markers
 * ("no longer available" pages) need two probes days apart, so a JD that merely
 * quotes such wording doesn't kill the posting on a single sighting.
 */
@Service
public class JobUrlCheckService implements ReportJobInactiveUseCase {

    private static final Logger log = LoggerFactory.getLogger(JobUrlCheckService.class);

    private static final int HARD_GONE_THRESHOLD = 1;
    private static final int SOFT_GONE_THRESHOLD = 2;

    private final JobRepositoryPort jobRepo;
    private final JobUrlProbePort urlProbe;
    private final JobSearchPort jobSearch;
    private final IgnoredJobRepositoryPort ignoredJobRepo;
    private final SavedJobRepositoryPort savedJobRepo;
    private final boolean enabled;
    private final int recheckDays;
    private final int batchSize;
    private final long politenessDelayMs;

    public JobUrlCheckService(JobRepositoryPort jobRepo, JobUrlProbePort urlProbe,
                              JobSearchPort jobSearch,
                              IgnoredJobRepositoryPort ignoredJobRepo,
                              SavedJobRepositoryPort savedJobRepo,
                              @Value("${app.job.url-check.enabled:true}") boolean enabled,
                              @Value("${app.job.url-check.recheck-days:3}") int recheckDays,
                              @Value("${app.job.url-check.batch-size:150}") int batchSize,
                              @Value("${app.job.url-check.politeness-delay-ms:300}") long politenessDelayMs) {
        this.jobRepo = jobRepo;
        this.urlProbe = urlProbe;
        this.jobSearch = jobSearch;
        this.ignoredJobRepo = ignoredJobRepo;
        this.savedJobRepo = savedJobRepo;
        this.enabled = enabled;
        this.recheckDays = recheckDays;
        this.batchSize = batchSize;
        this.politenessDelayMs = politenessDelayMs;
    }

    @Scheduled(cron = "${app.job.url-check.cron:0 30 */6 * * *}")
    public void checkJobUrls() {
        if (!enabled) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(recheckDays));
        List<Job> candidates = jobRepo.findUrlCheckCandidates(cutoff, batchSize);
        if (candidates.isEmpty()) return;

        int deactivated = 0;
        for (Job job : candidates) {
            if (applyProbe(job)) deactivated++;
            try {
                Thread.sleep(politenessDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("URL check: probed {} jobs, deactivated {}", candidates.size(), deactivated);
    }

    @Override
    public void reportInactive(UUID userId, UUID jobId) {
        // Hide for the reporting user right away, whatever the probe says.
        savedJobRepo.unsave(userId, jobId);
        if (!ignoredJobRepo.isIgnored(userId, jobId)) {
            ignoredJobRepo.save(new IgnoredJob(null, userId, jobId, "taken down", Instant.now()));
        }
        // Verify globally in the background — one user's report alone must not
        // deactivate a posting for everyone. (Explicit executor: @Async would be
        // self-invoked here and silently run synchronously.)
        jobRepo.findById(jobId).ifPresent(job ->
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    if (applyProbe(job)) {
                        log.info("User-reported job {} confirmed gone and deactivated", job.id());
                    }
                }));
    }

    /** Probes one job and applies the outcome. Returns true when the job was deactivated. */
    private boolean applyProbe(Job job) {
        if (job.url() == null || job.url().isBlank()) return false;
        UrlProbeOutcome outcome = urlProbe.probe(job.url());
        switch (outcome) {
            case ALIVE -> jobRepo.markUrlAlive(job.id());
            case INCONCLUSIVE -> jobRepo.markUrlCheckInconclusive(job.id());
            case GONE, GONE_SOFT -> {
                int threshold = outcome == UrlProbeOutcome.GONE ? HARD_GONE_THRESHOLD : SOFT_GONE_THRESHOLD;
                if (jobRepo.markUrlTakenDown(job.id(), threshold)) {
                    jobSearch.delete(job.id());
                    return true;
                }
            }
        }
        return false;
    }
}
