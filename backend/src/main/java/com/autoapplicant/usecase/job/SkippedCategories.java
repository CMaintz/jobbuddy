package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import java.util.List;
import java.util.UUID;

/** The job categories a user has said she never wants to see, for the search paths. */
final class SkippedCategories {

    private SkippedCategories() {}

    /** None for an anonymous query or a user who has not saved preferences. */
    static List<String> of(PreferencesRepositoryPort prefsRepo, UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return prefsRepo.findByUserId(userId)
                .map(UserPreferences::excludedCategoriesOrEmpty).orElse(List.of());
    }
}
