package com.autoapplicant.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.user.EnrichmentArea;
import com.autoapplicant.domain.user.UserPreferences;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrichmentScopeTest {

    private static UserPreferences skipping(String... categories) {
        return new UserPreferences(null, UUID.randomUUID(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, null, null, null, null, List.of(categories), null);
    }

    @Test
    void aCategoryIsSkippedOnlyWhenEveryUserSkipsIt() {
        // Bug: taking the union of skip lists stops enriching postings another user still
        // wants, so her matches for them lose their skills and requirements.
        EnrichmentScope scope = EnrichmentScope.of(List.of(skipping("SALES", "HR"), skipping("SALES")), DanishPlaces.shared());

        assertThat(scope.excludedCategories()).containsExactly("SALES");
    }

    @Test
    void withNoSavedPreferencesNothingIsSkipped() {
        // Bug: an empty intersection over no users is taken as "skip everything".
        assertThat(EnrichmentScope.of(List.of(), DanishPlaces.shared()).excludedCategories()).isEmpty();
    }

    private static UserPreferences livingIn(String home, int km) {
        return new UserPreferences(null, UUID.randomUUID(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, null, null, null, null, List.of(), new EnrichmentArea(home, km));
    }

    @Test
    void withNoSavedAreaTheDefaultIsAnHourAroundVibyJ() {
        // Bug: no saved preferences means no area at all, and the first sweep on a fresh
        // install enriches every posting in Denmark.
        EnrichmentScope scope = EnrichmentScope.of(List.of(), DanishPlaces.shared());

        assertThat(scope.areas()).containsExactly(
                new GeoArea(DanishPlaces.shared().locate("8260").orElseThrow(), 85));
    }

    @Test
    void everyUsersAreaCounts() {
        // Bug: only one user's area is used; the other user's postings are never enriched.
        EnrichmentScope scope = EnrichmentScope.of(
                List.of(livingIn("8260 Viby J", 85), livingIn("2100 København Ø", 40)),
                DanishPlaces.shared());

        assertThat(scope.areas()).extracting(GeoArea::radiusKm).containsExactlyInAnyOrder(85, 40);
    }
}
