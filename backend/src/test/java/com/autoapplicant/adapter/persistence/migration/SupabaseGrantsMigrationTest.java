package com.autoapplicant.adapter.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Supabase grants its Data API roles everything in {@code public}, and JobBuddy has no row-level
 * security, so the anon key would read her data the moment the Data API is on.
 *
 * <p>A container of its own: roles are cluster-wide, and an {@code anon} role left in the shared
 * container would change what every other migration test runs against.
 */
@Testcontainers(disabledWithoutDocker = true)
class SupabaseGrantsMigrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @Test
    void the_anon_role_can_read_neither_existing_nor_later_tables() throws SQLException {
        JdbcTemplate admin = new JdbcTemplate(adminDatabase());
        DataSource db = freshDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        admin.execute("CREATE ROLE anon NOLOGIN");
        try {
            // What Supabase sets up for the role that migrates, before any JobBuddy table exists.
            jdbc.execute("GRANT USAGE ON SCHEMA public TO anon");
            jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon");
            jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon");

            migrate(db);
            jdbc.execute("CREATE TABLE added_after_migrating (id int)");

            // Bug: without the revoke, the anon key reads every table through the Data API.
            assertThat(canSelect(jdbc, "public.jobs")).isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.role_table_grants WHERE grantee = 'anon'",
                    Integer.class)).isZero();
            // Bug: revoking only what exists leaves the default grant, so the next migration's
            // table is readable again.
            assertThat(canSelect(jdbc, "public.added_after_migrating")).isFalse();
        } finally {
            // Leaves the cluster without the role for the plain-Postgres case, whatever the order.
            jdbc.execute("DROP OWNED BY anon");
            admin.execute("DROP ROLE anon");
        }
    }

    @Test
    void plain_postgres_without_the_supabase_roles_still_migrates() throws SQLException {
        JdbcTemplate admin = new JdbcTemplate(adminDatabase());
        assertThat(admin.queryForObject(
                "SELECT count(*) FROM pg_roles WHERE rolname IN ('anon', 'authenticated')",
                Integer.class)).isZero();
        DataSource db = freshDatabase();

        // Bug: an unguarded REVOKE fails on "role does not exist" and stops dev, CI and every
        // test database from starting.
        migrate(db);

        assertThat(new JdbcTemplate(db).queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();
    }

    private static boolean canSelect(JdbcTemplate jdbc, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_table_privilege('anon', ?, 'SELECT')", Boolean.class, table));
    }

    private static void migrate(DataSource db) {
        Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
    }

    private static DataSource adminDatabase() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static DataSource freshDatabase() throws SQLException {
        String name = "grants_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
