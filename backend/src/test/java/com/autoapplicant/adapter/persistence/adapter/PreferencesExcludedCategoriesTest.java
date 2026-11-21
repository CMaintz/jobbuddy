package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
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
                false, "DAILY", null, null, null, List.of("SALES", "HR")));

        assertThat(adapter.findByUserId(user).orElseThrow().excludedCategories())
                .containsExactlyInAnyOrder("SALES", "HR");
    }
}
