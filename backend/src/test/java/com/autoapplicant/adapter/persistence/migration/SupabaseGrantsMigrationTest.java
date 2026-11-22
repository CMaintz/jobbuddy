package com.autoapplicant.adapter.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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

    private static final List<String> API_ROLES = List.of("anon", "authenticated");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @Test
    void the_api_roles_can_use_neither_existing_nor_later_tables_or_functions() throws SQLException {
        JdbcTemplate admin = new JdbcTemplate(adminDatabase());
        DataSource db = freshDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        for (String role : API_ROLES) {
            admin.execute("CREATE ROLE " + role + " NOLOGIN");
        }
        try {
            // What Supabase sets up for the role that migrates, before any JobBuddy table exists.
            for (String role : API_ROLES) {
                jdbc.execute("GRANT USAGE ON SCHEMA public TO " + role);
                jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO " + role);
                jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO " + role);
                jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO " + role);
            }

            migrate(db);
            jdbc.execute("CREATE TABLE added_after_migrating (id int)");
            jdbc.execute("CREATE FUNCTION added_after_migrating() RETURNS int"
                    + " LANGUAGE sql SECURITY DEFINER AS 'SELECT 1'");

            for (String role : API_ROLES) {
                // Bug: without the revoke, the anon key reads every table through the Data API.
                assertThat(canSelect(jdbc, role, "public.jobs")).as(role).isFalse();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.role_table_grants WHERE grantee = ?",
                        Integer.class, role)).as(role).isZero();
                // Bug: revoking only what exists leaves the default grant, so the next migration's
                // table is readable again.
                assertThat(canSelect(jdbc, role, "public.added_after_migrating")).as(role).isFalse();
                // Bug: a later SECURITY DEFINER function granted to the role is callable over RPC
                // and runs with the owner's rights. Counts the role's own grant only: EXECUTE
                // through PUBLIC is Postgres's default and outside what this migration changes.
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_proc p, aclexplode(p.proacl) a"
                                + " WHERE p.proname = 'added_after_migrating' AND a.grantee = ?::regrole",
                        Integer.class, role)).as(role).isZero();
            }
        } finally {
            // Leaves the cluster without the roles for the plain-Postgres case, whatever the order.
            for (String role : API_ROLES) {
                jdbc.execute("DROP OWNED BY " + role);
                admin.execute("DROP ROLE " + role);
            }
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

    private static boolean canSelect(JdbcTemplate jdbc, String role, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_table_privilege(?, ?, 'SELECT')", Boolean.class, role, table));
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
