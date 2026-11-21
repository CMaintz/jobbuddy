package com.autoapplicant.domain.job;

import com.autoapplicant.domain.user.EnrichmentArea;
import com.autoapplicant.domain.user.UserPreferences;
import java.util.ArrayList;
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
 * @param areas a posting placed in none of these is left out; empty means no limit
 */
public record EnrichmentScope(Set<String> excludedCategories, List<GeoArea> areas) {

    public EnrichmentScope {
        excludedCategories = Set.copyOf(excludedCategories);
        areas = List.copyOf(areas);
    }

    public static EnrichmentScope everything() {
        return new EnrichmentScope(Set.of(), List.of());
    }

    /**
     * The scope for these users: categories every one of them skips, and the union of their
     * areas. With no saved preferences, nothing is skipped and the area is the default.
     */
    public static EnrichmentScope of(List<UserPreferences> preferences, DanishPlaces places) {
        if (preferences.isEmpty()) {
            return new EnrichmentScope(Set.of(), List.of(areaOf(EnrichmentArea.DEFAULT, places)));
        }
        Set<String> skippedByAll = new HashSet<>(preferences.get(0).excludedCategoriesOrEmpty());
        List<GeoArea> areas = new ArrayList<>();
        for (UserPreferences prefs : preferences) {
            skippedByAll.retainAll(prefs.excludedCategoriesOrEmpty());
            areas.add(areaOf(prefs.enrichmentAreaOrDefault(), places));
        }
        return new EnrichmentScope(skippedByAll, areas.stream().distinct().toList());
    }

    /**
     * Saving validates the home, so one that cannot be placed here means the gazetteer changed
     * since; the default keeps enrichment running rather than stopping it.
     */
    private static GeoArea areaOf(EnrichmentArea area, DanishPlaces places) {
        EnrichmentArea usable = area.radiusInRange() ? area : EnrichmentArea.DEFAULT;
        GeoPoint home = places.locate(usable.home())
                .or(() -> places.locate(EnrichmentArea.DEFAULT.home()))
                .orElseThrow();
        return new GeoArea(home, usable.radiusKm());
    }
}
