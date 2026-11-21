package com.autoapplicant.domain.job;

import com.autoapplicant.domain.user.UserPreferences;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which postings are worth enriching, across every user of this install.
 *
 * <p>The queue is shared while preferences are per user, so a category is skipped only when
 * every user with saved preferences skips it: skipping on anyone's say-so would starve the others
 * of the skills and requirements their matches need. With no saved preferences nothing is skipped.
 *
 * @param excludedCategories JobCategory names left out of the queue
 */
public record EnrichmentScope(Set<String> excludedCategories) {

    public EnrichmentScope {
        excludedCategories = Set.copyOf(excludedCategories);
    }

    public static EnrichmentScope everything() {
        return new EnrichmentScope(Set.of());
    }

    public static EnrichmentScope of(List<UserPreferences> preferences) {
        if (preferences.isEmpty()) {
            return everything();
        }
        Set<String> skippedByAll = new HashSet<>(preferences.get(0).excludedCategoriesOrEmpty());
        for (UserPreferences prefs : preferences) {
            skippedByAll.retainAll(prefs.excludedCategoriesOrEmpty());
        }
        return new EnrichmentScope(skippedByAll);
    }
}
