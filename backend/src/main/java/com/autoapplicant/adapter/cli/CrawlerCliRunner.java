package com.autoapplicant.adapter.cli;

import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs the job crawler as a one-shot CLI command and exits.
 *
 * Usage (from repo root):
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=crawler"
 *
 * To crawl a specific source (LINKEDIN, JOBINDEX, etc.):
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=crawler --crawler.source=JOBINDEX"
 *
 * To force a full re-crawl (never stops early on already-known pages):
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=crawler --crawler.force"
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=crawler --crawler.source=JOBINDEX --crawler.force"
 */
@Component
@Profile("crawler")
@Order(2)
public class CrawlerCliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlerCliRunner.class);

    /** One drain pass; repeated until the queue is empty. */
    private static final int DRAIN_BATCH = 200;
    private static final java.time.Duration DRAIN_SLICE = java.time.Duration.ofMinutes(30);

    private final TriggerCrawlUseCase triggerCrawl;
    private final SweepUnenrichedJobsUseCase enrichmentSweep;
    private final EmbedJobsUseCase embedJobs;

    public CrawlerCliRunner(TriggerCrawlUseCase triggerCrawl,
                            SweepUnenrichedJobsUseCase enrichmentSweep,
                            EmbedJobsUseCase embedJobs) {
        this.triggerCrawl = triggerCrawl;
        this.enrichmentSweep = enrichmentSweep;
        this.embedJobs = embedJobs;
    }

    @Override
    public void run(ApplicationArguments args) {
        String source = args.containsOption("crawler.source")
                ? args.getOptionValues("crawler.source").get(0)
                : null;
        boolean force = args.containsOption("crawler.force");

        if (source != null) {
            log.info("Starting targeted crawl for source: {} (force={})", source, force);
            com.autoapplicant.domain.job.JobSource jobSource =
                    com.autoapplicant.domain.job.JobSource.valueOf(source.toUpperCase());
            if (force) {
                triggerCrawl.triggerSourceSyncForce(jobSource);
            } else {
                triggerCrawl.triggerSourceSync(jobSource);
            }
        } else {
            log.info("Starting full crawl for all sources (force={})", force);
            if (force) {
                triggerCrawl.triggerAllSyncForce();
            } else {
                triggerCrawl.triggerAllSync();
            }
        }

        banner("Crawl complete" + (source != null ? " [" + source + "]" : " [all sources]"));

        // The scheduled worker never gets a turn in CLI mode — the process exits. So the run
        // drains the queues itself, which is also what makes "the command finished" mean
        // "the postings are usable" rather than "the rows exist".
        drainEnrichment();
        drainEmbedding();
        banner("Done — postings are crawled, enriched and embedded");
    }

    private void drainEnrichment() {
        log.info("Draining the enrichment queue...");
        while (true) {
            SweepUnenrichedJobsUseCase.SweepResult result = enrichmentSweep.sweep(DRAIN_BATCH);
            if (result.total() == 0) {
                log.info("Enrichment queue empty.");
                return;
            }
            // Everything attempted failed and none of it was given up on: retrying immediately
            // would spin. The scheduled worker will come back to these after the retry delay.
            if (result.succeeded() == 0 && result.gaveUp() == 0) {
                log.warn("Enrichment made no progress on {} postings — stopping here rather than "
                         + "spinning; they stay PENDING and the worker retries later", result.total());
                return;
            }
        }
    }

    private void drainEmbedding() {
        log.info("Draining the embedding queue...");
        while (true) {
            EmbedJobsUseCase.EmbedResult result = embedJobs.embedPending(DRAIN_BATCH, DRAIN_SLICE);
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

    private static void banner(String message) {
        System.out.println();
        System.out.println("============================================");
        System.out.println("  " + message);
        System.out.println("============================================");
        System.out.println();
    }
}
