package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;

import java.util.List;

public interface JobSourceConnectorPort {
    JobSource getSource();
    List<RawJobData> fetchJobs(CrawlConfig config);
}
