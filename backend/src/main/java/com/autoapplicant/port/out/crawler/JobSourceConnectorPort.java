package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.job.JobSource;
public interface JobSourceConnectorPort {
    JobSource getSource();
    void fetchJobs(CrawlConfig config);
}
