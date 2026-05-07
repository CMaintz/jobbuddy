package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;

public interface SearchJobsUseCase {
    JobSearchResult searchJobs(JobSearchQuery query);
}
