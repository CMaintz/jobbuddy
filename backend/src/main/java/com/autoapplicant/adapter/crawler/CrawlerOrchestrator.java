package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.crawler.CrawlRunReport;
import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.CrawlOrchestrationPort;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final java.time.Duration heartbeatEvery;

    /**
     * One daemon thread for every crawl's heartbeat: a beat is a single small UPDATE, and a
     * daemon thread never keeps a finished one-shot run from exiting.
     */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEATS =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "crawl-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Sources with a crawl in progress in this JVM. Two crawls of one source race each other's
     * duplicate check against the insert, so the second is skipped. Held in memory rather than
     * read from the persisted running flag: the CLI runner crawls before the startup clear of
     * leftover flags runs, and this is a single-instance deployment, so memory is the whole truth.
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public CrawlerOrchestrator(List<JobSourceConnectorPort> connectors,
                                IngestionPipeline ingestionPipeline,
                                JobRepositoryPort jobRepository,
                                CrawlerStateRepositoryPort crawlerStateRepo,
                                Executor crawlerTaskExecutor) {
        this(connectors, ingestionPipeline, jobRepository, crawlerStateRepo, crawlerTaskExecutor,
                java.time.Duration.ofHours(2));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CrawlerOrchestrator(List<JobSourceConnectorPort> connectors,
                                IngestionPipeline ingestionPipeline,
                                JobRepositoryPort jobRepository,
                                CrawlerStateRepositoryPort crawlerStateRepo,
                                Executor crawlerTaskExecutor,
                                @org.springframework.beans.factory.annotation.Value("${app.crawler.stale-running-after:PT2H}")
                                java.time.Duration staleRunningAfter) {
        this.connectors = connectors;
        this.ingestionPipeline = ingestionPipeline;
        this.jobRepository = jobRepository;
        this.crawlerStateRepo = crawlerStateRepo;
        this.crawlerTaskExecutor = crawlerTaskExecutor;
        // Four beats per stale window, so one late or failed beat never makes a live crawl
        // look abandoned to a process that starts meanwhile.
        this.heartbeatEvery = staleRunningAfter.dividedBy(4);
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
        List<JobSourceConnectorPort> scheduled = scheduledConnectors();
        log.info("Starting scheduled crawl for {} sources", scheduled.size());
        reportConnectorHealth();
        CompletableFuture<?>[] runs = scheduled.stream()
                .map(connector -> CompletableFuture.runAsync(() -> runConnector(connector), crawlerTaskExecutor))
                .toArray(CompletableFuture[]::new);
        // One line saying the whole run is over. Without it a finished crawl and a hung one
        // look identical in the log.
        CompletableFuture.allOf(runs).whenComplete((ignored, error) -> {
            if (error != null) {
                log.error("Crawl run finished with errors: {}", error.getMessage(), error);
            } else {
                log.info("Crawl run complete - all {} sources finished", runs.length);
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
        List<CrawlerState> states = crawlerStateRepo.findAll();
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

    /** @return true when the crawl ran and recorded an error; a skipped crawl is not a failure */
    private boolean runConnectorInternal(JobSourceConnectorPort connector, boolean force) {
        String sourceName = connector.getSource().name();
        if (!inFlight.add(sourceName)) {
            log.warn("Skipping crawl of {}: a crawl of that source is already running", sourceName);
            return false;
        }
        try {
            return crawl(connector, force) != null;
        } finally {
            inFlight.remove(sourceName);
        }
    }

    /** @return the error the crawl recorded in crawler_state, or null when it finished cleanly */
    private String crawl(JobSourceConnectorPort connector, boolean force) {
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

        java.util.concurrent.ScheduledFuture<?> heartbeat = startHeartbeat(sourceName);
        String error = null;
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
                        IngestionPipeline.IngestResult result = ingestionPipeline.ingest(raw);
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
        } catch (Exception e) {
            log.error("Crawl failed for {}: {}", connector.getSource(), e.getMessage(), e);
            // Some exceptions carry no message; a null here would record the crawl as clean.
            error = e.getMessage() != null ? e.getMessage() : e.toString();
        } catch (Error e) {
            error = e.toString();
            throw e;
        } finally {
            heartbeat.cancel(false);
            // Mark the crawl finished, whatever happened, so the running flag never outlives it.
            // jobsFound is everything the source showed us; jobsIngested is what was actually
            // new. They were the same number before, which made a re-crawl of unchanged
            // postings look like a fresh haul.
            crawlerStateRepo.save(new CrawlerState(
                    sourceName, 0, startedAt, Instant.now(),
                    emitted.get() + knownSkipped.get(), ingestedNew.get(),
                    error, false, Instant.now()));
        }
        return error;
    }

    private java.util.concurrent.ScheduledFuture<?> startHeartbeat(String sourceName) {
        long everyMillis = Math.max(1, heartbeatEvery.toMillis());
        return HEARTBEATS.scheduleAtFixedRate(() -> beat(sourceName),
                everyMillis, everyMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** A failed beat is only logged: the crawl itself must not stop over its flag. */
    private void beat(String sourceName) {
        try {
            crawlerStateRepo.heartbeat(sourceName, Instant.now());
        } catch (RuntimeException e) {
            log.warn("Crawl heartbeat for {} failed: {}", sourceName, e.getMessage());
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
        List<JobSourceConnectorPort> scheduled = scheduledConnectors();
        log.info("Starting force crawl for {} sources", scheduled.size());
        scheduled.forEach(connector ->
                CompletableFuture.runAsync(() -> runConnectorForce(connector), crawlerTaskExecutor));
    }

    /** Synchronous variants used by the CLI runner: they block until all connectors finish. */
    @Override
    public CrawlRunReport runAllSync() {
        List<JobSourceConnectorPort> scheduled = scheduledConnectors();
        log.info("Starting synchronous crawl for {} sources", scheduled.size());
        CrawlRunReport report = runEachSync(scheduled, false);
        log.info("All sources finished.");
        return report;
    }

    @Override
    public CrawlRunReport runSourceSync(JobSource source) {
        return runOneSync(source, false);
    }

    @Override
    public CrawlRunReport runAllSyncForce() {
        List<JobSourceConnectorPort> scheduled = scheduledConnectors();
        log.info("Starting synchronous force crawl for {} sources", scheduled.size());
        CrawlRunReport report = runEachSync(scheduled, true);
        log.info("All sources finished (force mode).");
        return report;
    }

    @Override
    public CrawlRunReport runSourceSyncForce(JobSource source) {
        return runOneSync(source, true);
    }

    private CrawlRunReport runEachSync(List<JobSourceConnectorPort> toRun, boolean force) {
        List<JobSource> failed = new ArrayList<>();
        for (JobSourceConnectorPort connector : toRun) {
            if (runConnectorInternal(connector, force)) {
                failed.add(connector.getSource());
            }
        }
        return new CrawlRunReport(failed);
    }

    /**
     * A source that was asked for by name but has no connector (disabled, or not configured)
     * counts as failed: the caller asked for a crawl and none happened.
     */
    private CrawlRunReport runOneSync(JobSource source, boolean force) {
        return connectors.stream()
                .filter(c -> c.getSource() == source)
                .findFirst()
                .map(connector -> runEachSync(List.of(connector), force))
                .orElseGet(() -> {
                    log.warn("No connector found for source: {}", source);
                    return new CrawlRunReport(List.of(source));
                });
    }
}
