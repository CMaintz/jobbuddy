package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.port.in.job.SearchJobsUseCase;
import com.autoapplicant.port.out.job.JobSearchPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class SearchJobsService implements SearchJobsUseCase {

    private final JobSearchPort jobSearch;
    private final PreferencesRepositoryPort prefsRepo;

    public SearchJobsService(JobSearchPort jobSearch, PreferencesRepositoryPort prefsRepo) {
        this.jobSearch = jobSearch;
        this.prefsRepo = prefsRepo;
    }

    @Override
    public JobSearchResult searchJobs(JobSearchQuery query) {
        return jobSearch.search(query, SkippedCategories.of(prefsRepo, query.userId()));
    }
}
