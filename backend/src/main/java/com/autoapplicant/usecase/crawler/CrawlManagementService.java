package com.autoapplicant.usecase.crawler;

import com.autoapplicant.adapter.crawler.CrawlerOrchestrator;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import org.springframework.stereotype.Service;

@Service
public class CrawlManagementService implements TriggerCrawlUseCase {

    private final CrawlerOrchestrator orchestrator;

    public CrawlManagementService(CrawlerOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void triggerAll() {
        orchestrator.runAllCrawlers();
    }

    @Override
    public void triggerSource(JobSource source) {
        orchestrator.runSource(source);
    }
}
