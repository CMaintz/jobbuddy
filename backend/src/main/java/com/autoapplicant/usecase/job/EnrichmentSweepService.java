package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.job.EnrichJobUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnrichmentSweepService implements SweepUnenrichedJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSweepService.class);
    private static final long DELAY_MS = 2_000;

    private final JobRepositoryPort jobRepo;
    private final EnrichJobUseCase enrichJob;

    public EnrichmentSweepService(JobRepositoryPort jobRepo, EnrichJobUseCase enrichJob) {
        this.jobRepo = jobRepo;
        this.enrichJob = enrichJob;
    }

    @Override
    public SweepResult sweep(int limit) {
        List<Job> unenriched = jobRepo.findUnenriched(limit);
        int total = unenriched.size();
        int succeeded = 0;
        int failed = 0;

        for (int i = 0; i < unenriched.size(); i++) {
            Job job = unenriched.get(i);
            log.info("Enriching [{}/{}] {} ({})", i + 1, total, job.title(), job.id());

            try {
                Job enriched = enrichJob.enrich(job).get();
                if (enriched.aiSummary() != null) {
                    jobRepo.save(enriched);
                    succeeded++;
                } else {
                    log.warn("Enrichment returned no summary for job {}, skipping save", job.id());
                    failed++;
                }
            } catch (Exception e) {
                log.warn("Enrichment failed for job {}: {}", job.id(), e.getMessage());
                failed++;
            }

            if (i < unenriched.size() - 1) {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return new SweepResult(total, succeeded, failed);
    }
}
