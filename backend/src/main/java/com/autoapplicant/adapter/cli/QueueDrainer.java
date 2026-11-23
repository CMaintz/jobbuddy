package com.autoapplicant.adapter.cli;

import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Empties the enrichment and embedding queues, for the one-shot CLI commands.
 *
 * <p>The scheduled worker never gets a turn in a process that exits when its command finishes,
 * so a CLI run has to drain the queues itself. Shared by the crawl and sweep runners. A posting
 * is searchable once it is embedded; enrichment adds the summary, skills and requirements, and
 * a crawl run that has no chat provider leaves it to a process that does.
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

    /**
     * Enriches until the queue is empty, the ceiling is reached or a pass makes no progress.
     *
     * @param maxPostings ceiling across all passes; {@code Integer.MAX_VALUE} means "until empty"
     * @return false when a pass attempted postings and enriched none of them, which is what a
     *     provider outage or a missing login looks like; true otherwise, including an empty queue
     */
    public boolean drainEnrichment(int maxPostings) {
        log.info("Draining the enrichment queue...");
        boolean everyPassEnrichedSomething = true;
        int processed = 0;
        while (processed < maxPostings) {
            int batch = Math.min(BATCH, maxPostings - processed);
            SweepUnenrichedJobsUseCase.SweepResult result = enrichmentSweep.sweep(batch);
            if (result.total() == 0) {
                log.info("Enrichment queue empty.");
                return everyPassEnrichedSomething;
            }
            processed += result.total();
            if (result.succeeded() == 0) {
                everyPassEnrichedSomething = false;
            }
            // Everything attempted failed and nothing was given up on: another pass would
            // retry the same postings immediately and spin. They stay PENDING for the
            // scheduled worker to revisit after the retry delay.
            if (result.succeeded() == 0 && result.gaveUp() == 0) {
                log.warn("Enrichment made no progress on {} postings — stopping rather than "
                         + "spinning; they stay PENDING and are retried later", result.total());
                return false;
            }
        }
        log.info("Enrichment: reached the requested limit of {} postings.", maxPostings);
        return everyPassEnrichedSomething;
    }

    /**
     * Embeds until the queue is empty or a pass makes no progress.
     *
     * @return false when a pass attempted postings and embedded none of them. That is what a
     *     missing or revoked key looks like, and each such run spends an attempt on every
     *     posting until they are given up on, so the caller should fail rather than report done.
     */
    public boolean drainEmbedding() {
        log.info("Draining the embedding queue...");
        boolean everyPassEmbeddedSomething = true;
        while (true) {
            EmbedJobsUseCase.EmbedResult result = embedJobs.embedPending(BATCH, SLICE);
            if (result.total() == 0) {
                log.info("Embedding queue empty.");
                return everyPassEmbeddedSomething;
            }
            if (result.succeeded() == 0) {
                everyPassEmbeddedSomething = false;
            }
            if (result.succeeded() == 0 && result.gaveUp() == 0) {
                log.warn("Embedding made no progress on {} postings; stopping here", result.total());
                return false;
            }
        }
    }
}
