package com.autoapplicant.port.in.crawler;

import com.autoapplicant.domain.job.JobSource;

public interface TriggerCrawlUseCase {
    /** Fire-and-forget — used by the REST admin endpoint and the scheduler. */
    void triggerAll();
    void triggerSource(JobSource source);

    /** Blocking — used by the CLI runner so the process doesn't exit before crawling finishes. */
    void triggerAllSync();
    void triggerSourceSync(JobSource source);
}
