package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.EnrichmentStatus;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.job.EnrichmentSweepService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the enrichment queue draining.
 *
 * <p>The crawler leaves every new posting PENDING; this is what turns them into enriched ones. It
 * is a worker rather than a periodic clean-up: there is no other enrichment path, so the backlog
 * only shrinks when this runs.
 *
 * <p>Three things it deliberately does NOT do:
 * <ul>
 *   <li>Run on the scheduler thread. It waits on AI calls, and the scheduler pool is shared with
 *       expiry, URL checks and reminders — a slow batch there stops all of them.</li>
 *   <li>Overlap with itself. A tick that arrives while the last one is still working is skipped,
 *       not queued, or a slow provider quietly builds a pile-up.</li>
 *   <li>Compete with a running crawl. Ingest is cheap and enrichment is not; letting the crawl
 *       finish first gets the postings in faster and keeps the log readable.</li>
 * </ul>
 */
@Component
public class EnrichmentSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSweepScheduler.class);

    private final EnrichmentSweepService sweep;
    private final JobRepositoryPort jobRepo;
    private final CrawlerStateRepositoryPort crawlerStateRepo;
    private final Executor executor;
    private final boolean enabled;
    private final int batchSize;
    private final Duration budget;

    /** One drain at a time, however often the tick fires. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EnrichmentSweepScheduler(SweepUnenrichedJobsUseCase sweep,
                                    JobRepositoryPort jobRepo,
                                    CrawlerStateRepositoryPort crawlerStateRepo,
                                    @Qualifier("enrichmentWorkerExecutor") Executor executor,
                                    @Value("${app.enrichment.sweep.enabled:true}") boolean enabled,
                                    @Value("${app.enrichment.sweep.batch-size:200}") int batchSize,
                                    @Value("${app.enrichment.sweep.budget:PT20M}") Duration budget) {
        this.sweep = (EnrichmentSweepService) sweep;
        this.jobRepo = jobRepo;
        this.crawlerStateRepo = crawlerStateRepo;
        this.executor = executor;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.budget = budget;
    }

    @Scheduled(fixedDelayString = "${app.enrichment.sweep.interval-ms:300000}",
               initialDelayString = "${app.enrichment.sweep.initial-delay-ms:60000}")
    public void scheduledSweep() {
        if (!enabled) return;
        if (crawlIsRunning()) {
            log.debug("Enrichment: a crawl is in progress — standing down until it finishes");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("Enrichment: previous batch still running — skipping this tick");
            return;
        }
        // Off the scheduler thread: this waits on AI calls, and the scheduler is shared.
        executor.execute(() -> {
            try {
                drain();
            } catch (Exception e) {
                log.error("Enrichment worker failed: {}", e.getMessage(), e);
            } finally {
                running.set(false);
            }
        });
    }

    private void drain() {
        SweepUnenrichedJobsUseCase.SweepResult result = sweep.sweep(batchSize, budget);
        if (result.total() == 0) return;
        Map<EnrichmentStatus, Long> backlog = jobRepo.countByEnrichmentStatus();
        log.info("Enrichment batch done: {} attempted, {} enriched, {} failed, {} given up on. "
                 + "Backlog now {}",
                result.total(), result.succeeded(), result.failed(), result.gaveUp(), backlog);
    }

    /** True while any source is mid-crawl. */
    private boolean crawlIsRunning() {
        return crawlerStateRepo.findAll().stream().anyMatch(state -> state.isRunning());
    }
}
