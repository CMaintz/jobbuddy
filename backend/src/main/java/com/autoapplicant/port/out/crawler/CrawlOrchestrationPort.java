package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.job.JobSource;

/**
 * Driving side of the crawl subsystem — what the application can ask the
 * crawler infrastructure to do. Implemented by the crawler orchestrator adapter.
 */
public interface CrawlOrchestrationPort {
    void runAllCrawlers();
    void runSource(JobSource source);
    void runAllForce();
    void runSourceForce(JobSource source);
    void runAllSync();
    void runAllSyncForce();
    void runSourceSync(JobSource source);
    void runSourceSyncForce(JobSource source);
}
