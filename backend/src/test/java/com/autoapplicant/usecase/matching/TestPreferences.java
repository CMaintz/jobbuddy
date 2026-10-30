package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.user.UserPreferences;
import java.util.List;
import java.util.UUID;

/** Builds {@link UserPreferences} with nothing set except what a test names. */
final class TestPreferences {

    private final UUID userId;
    private List<String> locations = List.of();
    private List<String> municipalities = List.of();
    private List<String> positiveSignals = List.of();
    private List<String> negativeSignals = List.of();
    private List<String> excludedCompanies = List.of();
    private List<String> remoteTypes = List.of();
    private List<String> employmentTypes = List.of();
    private List<String> seniority = List.of();
    private List<String> industries = List.of();
    private Integer salaryMin;
    private Integer salaryMax;
    private Integer maxCommuteKm;

    TestPreferences(UUID userId) {
        this.userId = userId;
    }

    TestPreferences locations(String... v) { locations = List.of(v); return this; }
    TestPreferences municipalities(String... v) { municipalities = List.of(v); return this; }
    TestPreferences positiveSignals(String... v) { positiveSignals = List.of(v); return this; }
    TestPreferences negativeSignals(String... v) { negativeSignals = List.of(v); return this; }
    TestPreferences excludedCompanies(String... v) { excludedCompanies = List.of(v); return this; }
    TestPreferences remoteTypes(String... v) { remoteTypes = List.of(v); return this; }
    TestPreferences employmentTypes(String... v) { employmentTypes = List.of(v); return this; }
    TestPreferences seniority(String... v) { seniority = List.of(v); return this; }
    TestPreferences industries(String... v) { industries = List.of(v); return this; }
    TestPreferences maxCommuteKm(int km) { maxCommuteKm = km; return this; }

    TestPreferences salary(int min, int max) {
        salaryMin = min;
        salaryMax = max;
        return this;
    }

    UserPreferences build() {
        return new UserPreferences(UUID.randomUUID(), userId, locations, municipalities,
                positiveSignals, negativeSignals, excludedCompanies, remoteTypes, employmentTypes,
                seniority, industries, salaryMin, salaryMax, maxCommuteKm,
                false, null, null, null, null);
    }
}
