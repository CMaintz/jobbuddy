package com.autoapplicant.adapter.cli;

import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
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

    private final TriggerCrawlUseCase triggerCrawl;

    public CrawlerCliRunner(TriggerCrawlUseCase triggerCrawl) {
        this.triggerCrawl = triggerCrawl;
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

        log.info("Crawl finished — waiting for AI enrichment queue to drain before exit...");
        System.out.println();
        System.out.println("============================================");
        System.out.println("  Crawl complete" + (source != null ? " [" + source + "]" : " [all sources]"));
        System.out.println("  Waiting for enrichment to finish (watch");
        System.out.println("  logs for progress every 50 jobs)...");
        System.out.println("============================================");
        System.out.println();
    }
}
