package com.autoapplicant.adapter.cli;

import com.autoapplicant.domain.crawler.CrawlRunReport;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import com.autoapplicant.port.in.job.CheckJobUrlsUseCase;
import com.autoapplicant.port.in.job.ExpireStaleJobsUseCase;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * After the crawl it retires stale postings; pass --crawler.no-expiry to skip that, and
 * --crawler.url-check --crawler.runs-per-day=N to probe due posting URLs first, where N is how
 * often the workflow runs the crawl.
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
    private final ExpireStaleJobsUseCase expireJobs;
    private final CheckJobUrlsUseCase checkUrls;
    private final boolean enrichmentEnabled;
    private int exitCode;

    public CrawlerCliRunner(TriggerCrawlUseCase triggerCrawl, QueueDrainer drainer,
                            ExpireStaleJobsUseCase expireJobs, CheckJobUrlsUseCase checkUrls,
                            @Value("${app.enrichment.sweep.enabled:true}") boolean enrichmentEnabled) {
        this.triggerCrawl = triggerCrawl;
        this.drainer = drainer;
        this.expireJobs = expireJobs;
        this.checkUrls = checkUrls;
        this.enrichmentEnabled = enrichmentEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        String source = args.containsOption("crawler.source")
                ? args.getOptionValues("crawler.source").get(0)
                : null;
        boolean force = args.containsOption("crawler.force");
        Integer probeRunsPerDay = probeRunsPerDay(args);

        CrawlRunReport report = crawl(source, force);
        banner("Crawl complete" + (source != null ? " [" + source + "]" : " [all sources]"));

        // A one-shot process is never alive when the probe and expiry schedules fire, so the run
        // does both itself. The probe goes first: it is what refreshes lastSeenAt on live
        // postings that connectors no longer revisit, and expiry retires on that column.
        if (probeRunsPerDay != null) {
            checkUrls.checkDueUrls(probeRunsPerDay);
        }
        if (args.containsOption("crawler.no-expiry")) {
            log.info("Expiry is off for this run (--crawler.no-expiry)");
        } else {
            expireJobs.deactivateStaleJobs();
        }

        // The scheduled worker never gets a turn in CLI mode, because the process exits. Draining
        // here is what makes "the command finished" mean "the postings are usable". Enrichment
        // follows the worker's flag: a run with it off leaves postings PENDING, without spending
        // their attempts, for a process that has a chat provider to enrich them.
        if (enrichmentEnabled) {
            drainer.drainEnrichment(Integer.MAX_VALUE);
        } else {
            log.info("Enrichment is off (app.enrichment.sweep.enabled=false); embedding only");
        }
        boolean embedded = drainer.drainEmbedding();

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
        banner(enrichmentEnabled ? "Done: postings are crawled, enriched and embedded"
                : "Done: postings are crawled and embedded (not enriched)");
    }

    /**
     * How often the workflow runs this probe, or null when this run does not probe. Required
     * with --crawler.url-check and checked before crawling: the probe's batch is sized from it,
     * and only the workflow that schedules the run knows its cadence. A default would let a
     * workflow running less often than assumed under-probe silently, and expiry would then
     * retire live postings.
     */
    private static Integer probeRunsPerDay(ApplicationArguments args) {
        if (!args.containsOption("crawler.url-check")) {
            return null;
        }
        List<String> values = args.getOptionValues("crawler.runs-per-day");
        int runsPerDay = values == null || values.isEmpty() ? 0 : parsePositive(values.get(0));
        if (runsPerDay <= 0) {
            throw new IllegalArgumentException("--crawler.url-check needs --crawler.runs-per-day=N,"
                    + " the number of times a day the workflow runs this crawl");
        }
        return runsPerDay;
    }

    private static int parsePositive(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
