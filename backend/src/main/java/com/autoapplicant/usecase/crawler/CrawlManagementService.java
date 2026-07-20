package com.autoapplicant.usecase.crawler;

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
    public void triggerAllSync() {
        orchestrator.runAllSync();
    }

    @Override
    public void triggerAllSyncForce() {
        orchestrator.runAllSyncForce();
    }

    @Override
    public void triggerSourceSync(JobSource source) {
        orchestrator.runSourceSync(source);
    }

    @Override
    public void triggerSourceSyncForce(JobSource source) {
        orchestrator.runSourceSyncForce(source);
    }
}
