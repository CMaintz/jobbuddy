package com.autoapplicant.adapter.crawler;

import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the embedding queue draining, on the same single thread as the enrichment worker.
 *
 * <p>Sharing that executor is deliberate: the two must not run at once. Both call the same AI
 * provider, and a posting has to be enriched before it is worth embedding, so serialising them
 * keeps the ordering natural and the provider quota predictable.
 */
@Component
public class EmbeddingSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSweepScheduler.class);

    private final EmbedJobsUseCase embedJobs;
    private final CrawlerStateRepositoryPort crawlerStateRepo;
    private final Executor executor;
    private final boolean enabled;
    private final int batchSize;
    private final Duration budget;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EmbeddingSweepScheduler(EmbedJobsUseCase embedJobs,
                                   CrawlerStateRepositoryPort crawlerStateRepo,
                                   @Qualifier("enrichmentWorkerExecutor") Executor executor,
                                   @Value("${app.embedding.sweep.enabled:true}") boolean enabled,
                                   @Value("${app.embedding.sweep.batch-size:200}") int batchSize,
                                   @Value("${app.embedding.sweep.budget:PT10M}") Duration budget) {
        this.embedJobs = embedJobs;
        this.crawlerStateRepo = crawlerStateRepo;
        this.executor = executor;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.budget = budget;
    }

    @Scheduled(fixedDelayString = "${app.embedding.sweep.interval-ms:300000}",
               initialDelayString = "${app.embedding.sweep.initial-delay-ms:120000}")
    public void scheduledSweep() {
        if (!enabled) return;
        if (crawlerStateRepo.findAll().stream().anyMatch(s -> s.isRunning())) return;
        if (!running.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                embedJobs.embedPending(batchSize, budget);
            } catch (Exception e) {
                log.error("Embedding worker failed: {}", e.getMessage(), e);
            } finally {
                running.set(false);
            }
        });
    }
}
