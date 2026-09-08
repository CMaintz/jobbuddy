package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.job.EnrichJobUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Brings unenriched postings up to date. Every job the crawler saves starts PENDING,
 * so this catches both the ones enrichment never got to — the pool discards work when
 * its queue fills — and the ones where it failed.
 *
 * <p>Outcomes are written to the job. Without that a posting the model can never
 * summarise comes back on every pass forever, spending quota each time.
 */
@Service
public class EnrichmentSweepService implements SweepUnenrichedJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSweepService.class);
    private static final long DELAY_MS = 2_000;

    private final JobRepositoryPort jobRepo;
    private final EnrichJobUseCase enrichJob;
    private final int maxAttempts;
    private final Duration retryDelay;

    public EnrichmentSweepService(JobRepositoryPort jobRepo,
                                  EnrichJobUseCase enrichJob,
                                  @Value("${app.enrichment.max-attempts:4}") int maxAttempts,
                                  @Value("${app.enrichment.retry-delay:PT6H}") Duration retryDelay) {
        this.jobRepo = jobRepo;
        this.enrichJob = enrichJob;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    @Override
    public SweepResult sweep(int limit) {
        Instant retryBefore = Instant.now().minus(retryDelay);
        List<Job> queue = jobRepo.findForEnrichment(limit, maxAttempts, retryBefore);
        int total = queue.size();
        int succeeded = 0;
        int failed = 0;
        int gaveUp = 0;

        for (int i = 0; i < total; i++) {
            Job job = queue.get(i);
            log.info("Enriching [{}/{}] {} ({})", i + 1, total, job.title(), job.id());

            try {
                Job enriched = enrichJob.enrich(job).get();
                if (enriched.aiSummary() != null) {
                    jobRepo.save(enriched);
                    jobRepo.markEnriched(job.id());
                    succeeded++;
                } else {
                    // Not an exception, but not a result either — count it as an attempt
                    // so it cannot loop forever.
                    if (jobRepo.markEnrichmentFailed(job.id(), "no summary returned", maxAttempts)) gaveUp++;
                    failed++;
                }
            } catch (Exception e) {
                log.warn("Enrichment failed for job {}: {}", job.id(), e.getMessage());
                if (jobRepo.markEnrichmentFailed(job.id(), e.getMessage(), maxAttempts)) gaveUp++;
                failed++;
            }

            if (i < total - 1) {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return new SweepResult(total, succeeded, failed, gaveUp);
    }
}
