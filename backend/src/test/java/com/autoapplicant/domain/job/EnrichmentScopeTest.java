package com.autoapplicant.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.user.UserPreferences;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrichmentScopeTest {

    private static UserPreferences skipping(String... categories) {
        return new UserPreferences(null, UUID.randomUUID(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, null, null, null, null, List.of(categories));
    }

    @Test
    void aCategoryIsSkippedOnlyWhenEveryUserSkipsIt() {
        // Bug: taking the union of skip lists stops enriching postings another user still
        // wants, so her matches for them lose their skills and requirements.
        EnrichmentScope scope = EnrichmentScope.of(List.of(skipping("SALES", "HR"), skipping("SALES")));

        assertThat(scope.excludedCategories()).containsExactly("SALES");
    }

    @Test
    void withNoSavedPreferencesNothingIsSkipped() {
        // Bug: an empty intersection over no users is taken as "skip everything".
        assertThat(EnrichmentScope.of(List.of()).excludedCategories()).isEmpty();
    }
}
