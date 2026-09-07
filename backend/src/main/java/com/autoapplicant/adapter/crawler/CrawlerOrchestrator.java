package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class CrawlerOrchestrator {

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
    private final Executor crawlerTaskExecutor;

    public CrawlerOrchestrator(List<JobSourceConnectorPort> connectors,
                                IngestionPipeline ingestionPipeline,
                                JobRepositoryPort jobRepository,
                                Executor crawlerTaskExecutor) {
        this.connectors = connectors;
        this.ingestionPipeline = ingestionPipeline;
        this.jobRepository = jobRepository;
        this.crawlerTaskExecutor = crawlerTaskExecutor;
    }

    @Scheduled(cron = "${app.crawler.cron:0 0 */4 * * *}")
    public void runAllCrawlers() {
        log.info("Starting scheduled crawl for {} sources", connectors.size());
        connectors.forEach(connector ->
                CompletableFuture.runAsync(() -> runConnector(connector), crawlerTaskExecutor));
    }

    public void runConnector(JobSourceConnectorPort connector) {
        runConnectorInternal(connector, false);
    }

    public void runConnectorForce(JobSourceConnectorPort connector) {
        runConnectorInternal(connector, true);
    }

    private void runConnectorInternal(JobSourceConnectorPort connector, boolean force) {
        try {
            log.info("Crawling source: {} (force={})", connector.getSource(), force);
            CrawlConfig config = new CrawlConfig(
                    connector.getSource(),
                    PAGES_PER_RUN,
                    300L,
                    List.of(),
                    guid -> jobRepository.existsBySourceAndSourceJobId(connector.getSource(), guid),
                    ingestionPipeline::ingest,
                    force
            );
            connector.fetchJobs(config);
            log.info("Finished crawling: {}", connector.getSource());
        } catch (Exception e) {
            log.error("Crawl failed for {}: {}", connector.getSource(), e.getMessage(), e);
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
        log.info("Starting force crawl for {} sources", connectors.size());
        connectors.forEach(connector ->
                CompletableFuture.runAsync(() -> runConnectorForce(connector), crawlerTaskExecutor));
    }

    /** Synchronous variants used by the CLI runner — blocks until all connectors finish. */
    public void runAllSync() {
        log.info("Starting synchronous crawl for {} sources", connectors.size());
        connectors.forEach(this::runConnector);
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
        log.info("Starting synchronous force crawl for {} sources", connectors.size());
        connectors.forEach(this::runConnectorForce);
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
