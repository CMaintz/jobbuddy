package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchQuery;
import org.springframework.data.domain.Page;

public interface GetJobsUseCase {
    Page<Job> getJobs(JobSearchQuery query);
}
