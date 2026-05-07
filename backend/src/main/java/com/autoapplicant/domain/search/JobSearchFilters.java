package com.autoapplicant.domain.search;

import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.job.Seniority;

import java.time.Instant;
import java.util.List;

public record JobSearchFilters(
        String title,
        String company,
        String location,
        List<RemoteType> remoteTypes,
        Integer salaryMin,
        Integer salaryMax,
        List<String> technologies,
        List<Seniority> seniority,
        List<EmploymentType> employmentTypes,
        Instant postedAfter
) {}
