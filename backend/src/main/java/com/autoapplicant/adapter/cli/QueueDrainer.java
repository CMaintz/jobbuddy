package com.autoapplicant.adapter.cli;

import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Empties the enrichment and embedding queues, for the one-shot CLI commands.
 *
 * <p>The scheduled worker never gets a turn in a process that exits when its command finishes,
 * so a CLI run has to drain the queues itself. Shared by the crawl and sweep runners because
 * both need the same thing afterwards: a posting is only usable once it is enriched AND
 * embedded, and a command that leaves half of that undone has not really finished.
 */
@Component
public class QueueDrainer {

    private static final Logger log = LoggerFactory.getLogger(QueueDrainer.class);

    private static final int BATCH = 500;
    private static final Duration SLICE = Duration.ofMinutes(30);

    private final SweepUnenrichedJobsUseCase enrichmentSweep;
    private final EmbedJobsUseCase embedJobs;

    public QueueDrainer(SweepUnenrichedJobsUseCase enrichmentSweep, EmbedJobsUseCase embedJobs) {
        this.enrichmentSweep = enrichmentSweep;
        this.embedJobs = embedJobs;
    }

    /** Enriches until the queue is empty, then embeds until that queue is empty. */
    public void drainAll() {
        drainEnrichment(Integer.MAX_VALUE);
        drainEmbedding();
    }

    /**
     * @param maxPostings ceiling across all passes; {@code Integer.MAX_VALUE} means "until empty"
     */
    public void drainEnrichment(int maxPostings) {
        log.info("Draining the enrichment queue...");
        int processed = 0;
        while (processed < maxPostings) {
            int batch = Math.min(BATCH, maxPostings - processed);
            SweepUnenrichedJobsUseCase.SweepResult result = enrichmentSweep.sweep(batch);
            if (result.total() == 0) {
                log.info("Enrichment queue empty.");
                return;
            }
            processed += result.total();
            // Everything attempted failed and nothing was given up on: another pass would
            // retry the same postings immediately and spin. They stay PENDING for the
            // scheduled worker to revisit after the retry delay.
            if (result.succeeded() == 0 && result.gaveUp() == 0) {
                log.warn("Enrichment made no progress on {} postings — stopping rather than "
                         + "spinning; they stay PENDING and are retried later", result.total());
                return;
            }
        }
        log.info("Enrichment: reached the requested limit of {} postings.", maxPostings);
    }

    public void drainEmbedding() {
        log.info("Draining the embedding queue...");
        while (true) {
            EmbedJobsUseCase.EmbedResult result = embedJobs.embedPending(BATCH, SLICE);
            if (result.total() == 0) {
                log.info("Embedding queue empty.");
                return;
            }
            if (result.succeeded() == 0 && result.gaveUp() == 0) {
                log.warn("Embedding made no progress on {} postings — stopping here", result.total());
                return;
            }
        }
    }
}
