package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
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

    private final List<JobSourceConnectorPort> connectors;
    private final IngestionPipeline ingestionPipeline;
    private final Executor crawlerTaskExecutor;

    public CrawlerOrchestrator(List<JobSourceConnectorPort> connectors,
                                IngestionPipeline ingestionPipeline,
                                Executor crawlerTaskExecutor) {
        this.connectors = connectors;
        this.ingestionPipeline = ingestionPipeline;
        this.crawlerTaskExecutor = crawlerTaskExecutor;
    }

    @Scheduled(cron = "${app.crawler.cron:0 0 */4 * * *}")
    public void runAllCrawlers() {
        log.info("Starting scheduled crawl for {} sources", connectors.size());
        connectors.forEach(connector ->
                CompletableFuture.runAsync(() -> runConnector(connector), crawlerTaskExecutor));
    }

    public void runConnector(JobSourceConnectorPort connector) {
        try {
            log.info("Crawling source: {}", connector.getSource());
            CrawlConfig config = new CrawlConfig(connector.getSource(), 10, 1000L, List.of());
            connector.fetchJobs(config).forEach(ingestionPipeline::ingest);
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
}
