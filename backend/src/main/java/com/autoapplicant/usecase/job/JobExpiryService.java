package com.autoapplicant.usecase.job;

import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deactivates jobs that haven't been seen by any crawler for a configurable
 * number of days (default 30). Runs daily at 03:00.
 */
@Service
public class JobExpiryService {

    private static final Logger log = LoggerFactory.getLogger(JobExpiryService.class);

    private final JobRepositoryPort jobRepo;
    private final int staleDays;

    public JobExpiryService(JobRepositoryPort jobRepo,
                            @Value("${app.job.stale-days:30}") int staleDays) {
        this.jobRepo = jobRepo;
        this.staleDays = staleDays;
    }

    @Scheduled(cron = "${app.job.expiry-cron:0 0 3 * * *}")
    @Transactional
    public void deactivateStaleJobs() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(staleDays));
        int deactivated = jobRepo.deactivateStaleJobs(cutoff);
        if (deactivated > 0) {
            log.info("Deactivated {} stale jobs (not seen since {})", deactivated, cutoff);
        }
    }
}
