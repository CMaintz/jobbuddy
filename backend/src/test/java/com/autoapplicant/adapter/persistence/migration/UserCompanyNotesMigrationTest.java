package com.autoapplicant.adapter.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V036 moves the shared company notes onto (user, company) rows. Migrates a scratch database up to
 * V035, seeds pre-V036 data, then applies the rest and checks where each note landed.
 */
class UserCompanyNotesMigrationTest extends PostgresIntegrationTest {

    private static final String BEFORE_PER_USER_NOTES = "35";

    @Autowired JdbcConnectionDetails db;

    DriverManagerDataSource scratch;
    JdbcTemplate jdbc;

    @BeforeEach
    void migrateScratchDatabaseToV035() throws SQLException {
        String name = "v036_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        String url = db.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        scratch = new DriverManagerDataSource(url, db.getUsername(), db.getPassword());
        jdbc = new JdbcTemplate(scratch);
        flyway().target(BEFORE_PER_USER_NOTES).load().migrate();
    }

    @Test
    void a_note_goes_to_the_one_user_working_that_company_and_ambiguous_ones_are_dropped() {
        UUID alice = user();
        UUID bob = user();
        UUID applied = company("Applied to only by Alice", "Alice's research");
        UUID outreach = company("Outreach only by Bob", "Bob's research");
        UUID shared = company("Both of them", "Whose research?");
        UUID nobody = company("Nobody", "Orphaned research");
        UUID blank = company("Blank", "   ");
        application(alice, applied);
        application(alice, applied);
        outreach(bob, outreach);
        application(alice, shared);
        outreach(bob, shared);
        application(alice, blank);

        flyway().load().migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id, company_id, notes FROM user_company_notes ORDER BY notes");
        assertThat(rows).containsExactly(
                Map.of("user_id", alice, "company_id", applied, "notes", "Alice's research"),
                Map.of("user_id", bob, "company_id", outreach, "notes", "Bob's research"));
        assertThat(rows).noneMatch(r -> r.get("company_id").equals(nobody));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns"
                + " WHERE table_name = 'companies' AND column_name LIKE 'research_notes%'", Integer.class))
                .isZero();
    }

    private FluentConfiguration flyway() {
        return Flyway.configure().dataSource(scratch).locations("classpath:db/migration");
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }

    private UUID company(String name, String notes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO companies (id, name, research_notes, research_notes_updated_at)"
                + " VALUES (?, ?, ?, now())", id, name, notes);
        return id;
    }

    private void application(UUID userId, UUID companyId) {
        UUID job = UUID.randomUUID();
        jdbc.update("INSERT INTO jobs (id, source, url, title, company_name, company_id)"
                + " VALUES (?, 'JOBINDEX', ?, 'Developer', 'Acme ApS', ?)",
                job, "https://example.com/jobs/" + job, companyId);
        jdbc.update("INSERT INTO applications (user_id, job_id) VALUES (?, ?)", userId, job);
    }

    private void outreach(UUID userId, UUID companyId) {
        jdbc.update("INSERT INTO outreach_contact (user_id, company_id, company_name)"
                + " VALUES (?, ?, 'Acme ApS')", userId, companyId);
    }
}
