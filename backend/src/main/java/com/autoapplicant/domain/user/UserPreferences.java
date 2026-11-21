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
        Integer weeklyApplicationGoal,
        Instant createdAt,
        Instant updatedAt,
        /** Job categories (JobCategory names) left out of the feed, matching and enrichment. */
        List<String> excludedCategories
) {

    /** The excluded categories, never null: a client that omits the field excludes nothing. */
    public List<String> excludedCategoriesOrEmpty() {
        return excludedCategories != null ? excludedCategories : List.of();
    }
}
