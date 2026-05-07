package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.port.in.job.SearchJobsUseCase;
import com.autoapplicant.port.out.job.JobSearchPort;
import org.springframework.stereotype.Service;

@Service
public class SearchJobsService implements SearchJobsUseCase {

    private final JobSearchPort jobSearch;

    public SearchJobsService(JobSearchPort jobSearch) {
        this.jobSearch = jobSearch;
    }

    @Override
    public JobSearchResult searchJobs(JobSearchQuery query) {
        return jobSearch.search(query);
    }
}
