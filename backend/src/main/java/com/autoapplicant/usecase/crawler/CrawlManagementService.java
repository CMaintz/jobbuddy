package com.autoapplicant.usecase.crawler;

import com.autoapplicant.domain.crawler.CrawlRunReport;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import com.autoapplicant.port.out.crawler.CrawlOrchestrationPort;
import org.springframework.stereotype.Service;

@Service
public class CrawlManagementService implements TriggerCrawlUseCase {

    private final CrawlOrchestrationPort orchestrator;

    public CrawlManagementService(CrawlOrchestrationPort orchestrator) {
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

    @Override
    public void triggerAllForce() {
        orchestrator.runAllForce();
    }

    @Override
    public void triggerSourceForce(JobSource source) {
        orchestrator.runSourceForce(source);
    }

    @Override
    public CrawlRunReport triggerAllSync() {
        return orchestrator.runAllSync();
    }

    @Override
    public CrawlRunReport triggerAllSyncForce() {
        return orchestrator.runAllSyncForce();
    }

    @Override
    public CrawlRunReport triggerSourceSync(JobSource source) {
        return orchestrator.runSourceSync(source);
    }

    @Override
    public CrawlRunReport triggerSourceSyncForce(JobSource source) {
        return orchestrator.runSourceSyncForce(source);
    }
}
