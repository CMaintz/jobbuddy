package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.user.EnrichmentArea;
import com.autoapplicant.domain.user.UserPreferences;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PreferencesPersistenceAdapter.class)
class PreferencesExcludedCategoriesTest extends PostgresIntegrationTest {

    @Autowired PreferencesPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theSkippedCategoriesSurviveASaveAndALoad() {
        // Bug: the mapper drops the new field one way or the other, so the setting appears to
        // save and is empty the next time Settings opens.
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", user, user + "@example.com");

        adapter.save(new UserPreferences(null, user, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, "DAILY", null, null, null, List.of("SALES", "HR"), null));

        assertThat(adapter.findByUserId(user).orElseThrow().excludedCategories())
                .containsExactlyInAnyOrder("SALES", "HR");
    }

    @Test
    void theCommuteAreaSurvivesASaveAndALoad() {
        // Bug: the area is not mapped, so Settings always shows the default and the queue
        // never uses her own home.
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", user, user + "@example.com");

        adapter.save(new UserPreferences(null, user, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, "DAILY", null, null, null, List.of(), new EnrichmentArea("8000 Aarhus C", 60)));

        assertThat(adapter.findByUserId(user).orElseThrow().enrichmentArea())
                .isEqualTo(new EnrichmentArea("8000 Aarhus C", 60));
    }
}
