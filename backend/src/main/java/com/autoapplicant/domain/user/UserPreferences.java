package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserPreferences(
        UUID id,
        UUID userId,
        List<String> preferredLocations,
        List<String> preferredMunicipalities,
        List<String> positiveSignals,
        List<String> negativeSignals,
        List<String> excludedCompanies,
        List<String> preferredRemoteTypes,
        List<String> preferredEmploymentTypes,
        List<String> preferredSeniority,
        List<String> preferredIndustries,
        Integer salaryMin,
        Integer salaryMax,
        Integer maxCommuteKm,
        boolean notificationEnabled,
        String notificationFrequency,
        Instant createdAt,
        Instant updatedAt
) {}
