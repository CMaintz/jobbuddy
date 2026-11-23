package com.autoapplicant.adapter.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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
 * V040 moves compliance frameworks from Security to Domain. A skill row keeps its own copy of the
 * category from the day it was imported, and that copy wins over the taxonomy, so existing users
 * are only fixed if the copies move too. Seeds such rows on a scratch database at V038, then
 * applies the rest.
 */
class RoleNeutralCvMigrationTest extends PostgresIntegrationTest {

    private static final String BEFORE_ROLE_NEUTRAL_CV = "38";

    @Autowired JdbcConnectionDetails db;

    JdbcTemplate jdbc;
    DriverManagerDataSource scratch;

    @BeforeEach
    void migrateScratchDatabaseToV038() throws SQLException {
        String name = "v040_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        String url = db.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        scratch = new DriverManagerDataSource(url, db.getUsername(), db.getPassword());
        jdbc = new JdbcTemplate(scratch);
        flyway().target(BEFORE_ROLE_NEUTRAL_CV).load().migrate();
    }

    @Test
    void imported_compliance_skills_move_to_domain_and_everything_else_stays() {
        UUID user = user();
        UUID gdpr = skill(user, "GDPR", "Security");
        UUID nis2 = skill(user, "NIS2", "Security");
        UUID keycloak = skill(user, "Keycloak", "Security");
        UUID handFiled = skill(user(), "ISO 27001", "Compliance");

        flyway().load().migrate();

        assertThat(category(gdpr)).isEqualTo("Domain");
        assertThat(category(nis2)).isEqualTo("Domain");
        assertThat(category(keycloak)).isEqualTo("Security");
        assertThat(category(handFiled)).isEqualTo("Compliance");
    }

    private FluentConfiguration flyway() {
        return Flyway.configure().dataSource(scratch).locations("classpath:db/migration");
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }

    /** A profile skill as ProfileSkillIngestion writes it: linked to its row, category copied. */
    private UUID skill(UUID user, String name, String category) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO profile_skills (id, user_id, skill_name, taxonomy_id, category)"
                + " SELECT ?, ?, ?, t.id, ? FROM skill_taxonomy t WHERE t.name = ?",
                id, user, name, category, name);
        return id;
    }

    private String category(UUID skill) {
        return jdbc.queryForObject("SELECT category FROM profile_skills WHERE id = ?", String.class, skill);
    }
}
