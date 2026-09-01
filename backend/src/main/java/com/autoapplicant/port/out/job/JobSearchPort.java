package com.autoapplicant.port.out.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;

import java.util.List;
import java.util.UUID;

public interface JobSearchPort {
    void index(Job job);
    void delete(UUID jobId);
    JobSearchResult search(JobSearchQuery query);
}
