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
    private final QueueDrainer drainer;

    public CrawlerCliRunner(TriggerCrawlUseCase triggerCrawl, QueueDrainer drainer) {
        this.triggerCrawl = triggerCrawl;
        this.drainer = drainer;
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

        // The scheduled worker never gets a turn in CLI mode — the process exits. Draining here
        // is what makes "the command finished" mean "the postings are usable".
        drainer.drainAll();
        banner("Done — postings are crawled, enriched and embedded");
    }

    private static void banner(String message) {
        System.out.println();
        System.out.println("============================================");
        System.out.println("  " + message);
        System.out.println("============================================");
        System.out.println();
    }
}
