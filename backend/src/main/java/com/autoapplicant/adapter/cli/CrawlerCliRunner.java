package com.autoapplicant.adapter.cli;

import com.autoapplicant.domain.crawler.CrawlRunReport;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs the job crawler as a one-shot CLI command and exits: 0 when every source it crawled
 * finished, 1 when any failed or when embedding failed for every posting it tried. A failed
 * source does not stop the others or the drain, so one broken feed still leaves the rest of
 * the run's postings usable.
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
public class CrawlerCliRunner implements OneShotCommand {

    private static final Logger log = LoggerFactory.getLogger(CrawlerCliRunner.class);

    private final TriggerCrawlUseCase triggerCrawl;
    private final QueueDrainer drainer;
    private int exitCode;

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

        CrawlRunReport report = crawl(source, force);
        banner("Crawl complete" + (source != null ? " [" + source + "]" : " [all sources]"));

        // The scheduled worker never gets a turn in CLI mode, because the process exits. Draining
        // here is what makes "the command finished" mean "the postings are usable".
        boolean embedded = drainer.drainAll();

        if (report.anyFailed()) {
            log.error("Crawl failed for {}; see crawler_state.last_error and the log above",
                    report.failedSources());
            exitCode = 1;
        }
        if (!embedded) {
            log.error("Embedding failed for every posting in a pass; check the embedding provider "
                      + "and its key before the attempts run out");
            exitCode = 1;
        }
        banner("Done: postings are crawled, enriched and embedded");
    }

    private CrawlRunReport crawl(String source, boolean force) {
        if (source == null) {
            log.info("Starting full crawl for all sources (force={})", force);
            return force ? triggerCrawl.triggerAllSyncForce() : triggerCrawl.triggerAllSync();
        }
        log.info("Starting targeted crawl for source: {} (force={})", source, force);
        JobSource jobSource = JobSource.valueOf(source.toUpperCase(Locale.ROOT));
        return force
                ? triggerCrawl.triggerSourceSyncForce(jobSource)
                : triggerCrawl.triggerSourceSync(jobSource);
    }

    /** 1 when a source this run crawled failed, or a pass embedded nothing it attempted. */
    @Override
    public int getExitCode() {
        return exitCode;
    }

    private static void banner(String message) {
        System.out.println();
        System.out.println("============================================");
        System.out.println("  " + message);
        System.out.println("============================================");
        System.out.println();
    }
}
