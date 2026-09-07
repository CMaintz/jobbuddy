package com.autoapplicant.domain.search;

import com.autoapplicant.domain.job.Job;

import java.util.List;
import java.util.Map;

public record JobSearchResult(
        List<Job> jobs,
        long total,
        int page,
        int size,
        Map<String, Object> facets
) {}
