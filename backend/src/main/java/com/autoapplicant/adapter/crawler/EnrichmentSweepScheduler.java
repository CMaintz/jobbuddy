package com.autoapplicant.adapter.crawler;

import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the enrichment sweep on a schedule.
 *
 * <p>Enrichment is fired off asynchronously as the crawler saves each job, on a pool
 * that discards work when its queue fills. That is the right trade for bulk crawling,
 * but it means some postings are never enriched, and until now the only thing that
 * recovered them was a CLI runner someone had to remember to invoke. Unrun, jobs sit
 * unenriched indefinitely: no summary, no tiered skills, no contact — and nothing to
 * say so.
 *
 * <p>Offset half an hour from the crawler so a sweep is not competing with the ingest
 * it is meant to clean up after.
 */
@Component
public class EnrichmentSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSweepScheduler.class);

    private final SweepUnenrichedJobsUseCase sweep;
    private final JobRepositoryPort jobRepo;
    private final boolean enabled;
    private final int batchSize;

    public EnrichmentSweepScheduler(SweepUnenrichedJobsUseCase sweep,
                                    JobRepositoryPort jobRepo,
                                    @Value("${app.enrichment.sweep.enabled:true}") boolean enabled,
                                    @Value("${app.enrichment.sweep.batch-size:40}") int batchSize) {
        this.sweep = sweep;
        this.jobRepo = jobRepo;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.enrichment.sweep.cron:0 30 * * * *}")
    public void scheduledSweep() {
        if (!enabled) return;

        SweepUnenrichedJobsUseCase.SweepResult result = sweep.sweep(batchSize);
        if (result.total() == 0) return;

        log.info("Enrichment sweep: {} attempted, {} enriched, {} failed, {} given up on. Backlog now {}",
                result.total(), result.succeeded(), result.failed(), result.gaveUp(),
                jobRepo.countByEnrichmentStatus());
    }
}
