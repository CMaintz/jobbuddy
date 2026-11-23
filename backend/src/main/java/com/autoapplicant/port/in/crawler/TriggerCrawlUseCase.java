package com.autoapplicant.port.in.crawler;

import com.autoapplicant.domain.crawler.CrawlRunReport;
import com.autoapplicant.domain.job.JobSource;

public interface TriggerCrawlUseCase {
    /** Fire-and-forget — used by the REST admin endpoint and the scheduler. */
    void triggerAll();
    void triggerSource(JobSource source);

    /**
     * Force variants — never stop early due to already-known pages.
     * Use for first-time backfills or forced re-crawls during development.
     */
    void triggerAllForce();
    void triggerSourceForce(JobSource source);

    /**
     * Blocking, for the CLI runner: returns once every requested source has finished, and
     * reports which of them failed so the process can exit non-zero.
     */
    CrawlRunReport triggerAllSync();
    CrawlRunReport triggerAllSyncForce();
    CrawlRunReport triggerSourceSync(JobSource source);
    CrawlRunReport triggerSourceSyncForce(JobSource source);
}
