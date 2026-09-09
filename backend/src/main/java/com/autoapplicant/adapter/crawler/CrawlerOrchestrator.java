package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.CrawlOrchestrationPort;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CrawlerOrchestrator implements CrawlOrchestrationPort {

    private static final Logger log = LoggerFactory.getLogger(CrawlerOrchestrator.class);

    /**
     * Hard safety ceiling on pages per run — purely a runaway-loop guard.
     * The real stop conditions are: (a) the connector gets an empty page, or
     * (b) isKnownGuid fires, meaning everything deeper is already in the DB.
     * On a first backfill those conditions exhaust all available pages naturally.
     * On incremental runs isKnownGuid stops the crawl after the first page or two.
     * This number should never be the binding constraint in normal operation.
     */
    private static final int PAGES_PER_RUN = 10_000;

    private final List<JobSourceConnectorPort> connectors;
    private final IngestionPipeline ingestionPipeline;
    private final JobRepositoryPort jobRepository;
    private final CrawlerStateRepositoryPort crawlerStateRepo;
    private final Executor crawlerTaskExecutor;

    public CrawlerOrchestrator(List<JobSourceConnectorPort> connectors,
                                IngestionPipeline ingestionPipeline,
                                JobRepositoryPort jobRepository,
                                CrawlerStateRepositoryPort crawlerStateRepo,
                                Executor crawlerTaskExecutor) {
        this.connectors = connectors;
        this.ingestionPipeline = ingestionPipeline;
        this.jobRepository = jobRepository;
        this.crawlerStateRepo = crawlerStateRepo;
        this.crawlerTaskExecutor = crawlerTaskExecutor;
    }

    /**
     * Connectors that participate in the bulk "run all sources" operations. Sources
     * with their own controlled cadence (e.g. LinkedIn) opt out via
     * {@link JobSourceConnectorPort#includeInDefaultSchedule()} and are only ever run
     * by an explicit per-source trigger.
     */
    private List<JobSourceConnectorPort> scheduledConnectors() {
        return connectors.stream().filter(JobSourceConnectorPort::includeInDefaultSchedule).toList();
    }

    @Scheduled(cron = "${app.crawler.cron:0 0 */4 * * *}")
    public void runAllCrawlers() {
        var scheduled = scheduledConnectors();
        log.info("Starting scheduled crawl for {} sources", scheduled.size());
        reportConnectorHealth();
        var runs = scheduled.stream()
                .map(connector -> CompletableFuture.runAsync(() -> runConnector(connector), crawlerTaskExecutor))
                .toArray(CompletableFuture[]::new);
        // One line saying the whole run is over. Without it a finished crawl and a hung one
        // look identical in the log.
        CompletableFuture.allOf(runs).whenComplete((ignored, error) -> {
            if (error != null) {
                log.error("Crawl run finished with errors: {}", error.getMessage(), error);
            } else {
                log.info("Crawl run complete — all {} sources finished", runs.length);
            }
            reportConnectorHealth();
        });
    }

    /**
     * Which sources are actually producing, and which have gone quiet.
     *
     * <p>A connector that silently stops working — a feed moves, a site adds a bot wall, a
     * company slug changes — looks exactly like a connector with nothing new to report. The
     * difference is only visible next to the other sources and against the clock, so it is
     * printed as a table rather than left for someone to notice.
     */
    public void reportConnectorHealth() {
        var states = crawlerStateRepo.findAll();
        if (states.isEmpty()) return;
        StringBuilder table = new StringBuilder("Connector health:\n");
        states.stream()
                .sorted(java.util.Comparator.comparing(CrawlerState::source))
                .forEach(state -> {
                    String age = state.lastCrawlFinishedAt() == null ? "never run"
                            : humanAge(java.time.Duration.between(state.lastCrawlFinishedAt(), Instant.now()));
                    String health = state.lastError() != null ? "ERROR: " + state.lastError()
                            : state.jobsFound() == 0 ? "produced nothing last run"
                            : "ok";
                    table.append(String.format("  %-14s last run %-12s seen %-6d new %-6d  %s%n",
                            state.source(), age, state.jobsFound(), state.jobsIngested(), health));
                });
        log.info(table.toString().stripTrailing());
    }

    private static String humanAge(java.time.Duration d) {
        if (d.toHours() >= 48) return d.toDays() + "d ago";
        if (d.toHours() >= 1) return d.toHours() + "h ago";
        return Math.max(0, d.toMinutes()) + "m ago";
    }

    public void runConnector(JobSourceConnectorPort connector) {
        runConnectorInternal(connector, false);
    }

    public void runConnectorForce(JobSourceConnectorPort connector) {
        runConnectorInternal(connector, true);
    }

    private void runConnectorInternal(JobSourceConnectorPort connector, boolean force) {
        String sourceName = connector.getSource().name();
        // Four numbers, because one was hiding three different things. A connector that
        // re-emits everything (Greenhouse) and one that skips what it recognises (Teamtailor)
        // used to report the same field, so their totals meant different things and could not
        // be compared.
        AtomicInteger emitted = new AtomicInteger(0);
        AtomicInteger ingestedNew = new AtomicInteger(0);
        AtomicInteger refreshed = new AtomicInteger(0);
        AtomicInteger knownSkipped = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger crossListed = new AtomicInteger(0);
        Instant startedAt = Instant.now();

        // Mark crawl as running
        crawlerStateRepo.save(new CrawlerState(
                sourceName, 0, startedAt, null, 0, 0, null, true, Instant.now()));

        try {
            log.info("Crawling source: {} (force={})", connector.getSource(), force);
            CrawlConfig config = new CrawlConfig(
                    connector.getSource(),
                    PAGES_PER_RUN,
                    300L,
                    List.of(),
                    guid -> jobRepository.existsBySourceAndSourceJobId(connector.getSource(), guid),
                    raw -> {
                        emitted.incrementAndGet();
                        var result = ingestionPipeline.ingest(raw);
                        switch (result.outcome()) {
                            case NEW -> ingestedNew.incrementAndGet();
                            case REFRESHED -> refreshed.incrementAndGet();
                            case FAILED -> failed.incrementAndGet();
                        }
                        if (result.crossListed()) crossListed.incrementAndGet();
                    },
                    // A posting the connector recognised and did not re-emit is still a posting
                    // we have just seen alive. Without this its lastSeenAt ages until the expiry
                    // sweep deactivates a role that is open and advertised.
                    guid -> {
                        knownSkipped.incrementAndGet();
                        jobRepository.markSeenBySourceJobId(connector.getSource(), guid, Instant.now());
                    },
                    force
            );
            connector.fetchJobs(config);
            int seen = emitted.get() + knownSkipped.get();
            log.info("Finished crawling: {} — {} postings seen: {} new, {} refreshed, "
                     + "{} already known (skipped by connector), {} failed, {} cross-listings",
                    connector.getSource(), seen, ingestedNew.get(), refreshed.get(),
                    knownSkipped.get(), failed.get(), crossListed.get());

            // Mark crawl as finished successfully. jobsFound is everything the source showed us;
            // jobsIngested is what was actually new. They were the same number before, which made
            // a re-crawl of unchanged postings look like a fresh haul.
            crawlerStateRepo.save(new CrawlerState(
                    sourceName, 0, startedAt, Instant.now(),
                    seen, ingestedNew.get(), null, false, Instant.now()));

        } catch (Exception e) {
            log.error("Crawl failed for {}: {}", connector.getSource(), e.getMessage(), e);
            crawlerStateRepo.save(new CrawlerState(
                    sourceName, 0, startedAt, Instant.now(),
                    emitted.get() + knownSkipped.get(), ingestedNew.get(),
                    e.getMessage(), false, Instant.now()));
        }
    }

    public void runSource(JobSource source) {
        connectors.stream()
                .filter(c -> c.getSource() == source)
                .findFirst()
                .ifPresent(connector -> CompletableFuture.runAsync(
                        () -> runConnector(connector), crawlerTaskExecutor));
    }

    public void runSourceForce(JobSource source) {
        connectors.stream()
                .filter(c -> c.getSource() == source)
                .findFirst()
                .ifPresent(connector -> CompletableFuture.runAsync(
                        () -> runConnectorForce(connector), crawlerTaskExecutor));
    }

    public void runAllForce() {
        var scheduled = scheduledConnectors();
        log.info("Starting force crawl for {} sources", scheduled.size());
        scheduled.forEach(connector ->
                CompletableFuture.runAsync(() -> runConnectorForce(connector), crawlerTaskExecutor));
    }

    /** Synchronous variants used by the CLI runner — blocks until all connectors finish. */
    public void runAllSync() {
        var scheduled = scheduledConnectors();
        log.info("Starting synchronous crawl for {} sources", scheduled.size());
        scheduled.forEach(this::runConnector);
        log.info("All sources finished.");
    }

    public void runSourceSync(JobSource source) {
        connectors.stream()
                .filter(c -> c.getSource() == source)
                .findFirst()
                .ifPresentOrElse(
                        this::runConnector,
                        () -> log.warn("No connector found for source: {}", source));
    }

    public void runAllSyncForce() {
        var scheduled = scheduledConnectors();
        log.info("Starting synchronous force crawl for {} sources", scheduled.size());
        scheduled.forEach(this::runConnectorForce);
        log.info("All sources finished (force mode).");
    }

    public void runSourceSyncForce(JobSource source) {
        connectors.stream()
                .filter(c -> c.getSource() == source)
                .findFirst()
                .ifPresentOrElse(
                        this::runConnectorForce,
                        () -> log.warn("No connector found for source: {}", source));
    }
}
