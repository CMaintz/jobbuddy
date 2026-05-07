package com.autoapplicant.port.in.crawler;

import com.autoapplicant.domain.job.JobSource;

public interface TriggerCrawlUseCase {
    void triggerAll();
    void triggerSource(JobSource source);
}
