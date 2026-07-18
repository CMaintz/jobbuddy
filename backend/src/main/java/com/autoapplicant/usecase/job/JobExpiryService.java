package com.autoapplicant.usecase.job;

import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.JobSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deactivates jobs that haven't been seen by any crawler (or confirmed alive by
 * a URL probe) for a configurable number of days (default 30). Runs daily at
 * 03:00. Manual jobs are exempt — no crawler ever refreshes them.
 */
@Service
public class JobExpiryService {

    private static final Logger log = LoggerFactory.getLogger(JobExpiryService.class);

    private final JobRepositoryPort jobRepo;
    private final JobSearchPort jobSearch;
    private final int staleDays;

    public JobExpiryService(JobRepositoryPort jobRepo, JobSearchPort jobSearch,
                            @Value("${app.job.stale-days:30}") int staleDays) {
        this.jobRepo = jobRepo;
        this.jobSearch = jobSearch;
        this.staleDays = staleDays;
    }

    @Scheduled(cron = "${app.job.expiry-cron:0 0 3 * * *}")
    @Transactional
    public void deactivateStaleJobs() {
        deactivateDeadlineExpired();

        Instant cutoff = Instant.now().minus(Duration.ofDays(staleDays));
        List<UUID> staleIds = jobRepo.findStaleActiveJobIds(cutoff);
        if (staleIds.isEmpty()) return;
        int deactivated = jobRepo.deactivateStaleJobs(cutoff);
        // Expired postings must also leave the search index, or search keeps surfacing them.
        staleIds.forEach(jobSearch::delete);
        log.info("Deactivated {} stale jobs (not seen since {})", deactivated, cutoff);
    }

    /**
     * A passed application deadline is a stronger signal than staleness: the
     * posting may still render, but applying is pointless. The deadline day
     * itself still counts as applicable.
     */
    private void deactivateDeadlineExpired() {
        List<UUID> expiredIds = jobRepo.deactivateDeadlineExpiredJobs(java.time.LocalDate.now());
        if (expiredIds.isEmpty()) return;
        expiredIds.forEach(jobSearch::delete);
        log.info("Deactivated {} jobs whose application deadline passed", expiredIds.size());
    }
}
