package com.autoapplicant.adapter.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationTest extends PostgresIntegrationTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void every_migration_applies_cleanly_to_an_empty_database() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty()
                .allSatisfy(m -> assertThat(m.getState().isFailed()).isFalse());
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void search_relies_on_pgvector_and_the_generated_search_vector_column() {
        Integer vectorExtension = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        String searchVectorGenerated = jdbc.queryForObject(
                "SELECT is_generated FROM information_schema.columns"
                        + " WHERE table_name = 'jobs' AND column_name = 'search_vector'",
                String.class);

        assertThat(vectorExtension).isEqualTo(1);
        assertThat(searchVectorGenerated).isEqualTo("ALWAYS");
    }
}
