package com.autoapplicant.domain.user;

import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.RemoteType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Profile(
        UUID id,
        UUID userId,
        String headline,
        String summary,
        Integer yearsExperience,
        List<String> skills,
        List<String> technologies,
        List<String> languages,
        Integer desiredSalaryMin,
        Integer desiredSalaryMax,
        String desiredCurrency,
        RemoteType remotePreference,
        EmploymentType employmentTypePreference,
        Instant createdAt,
        Instant updatedAt
) {}
