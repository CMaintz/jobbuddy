package com.autoapplicant.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * With Flyway off in the crawler profile, nothing else notices a crawl built from newer code
 * than the schema; it would fail on a missing column halfway through writing postings.
 */
@Testcontainers(disabledWithoutDocker = true)
class SchemaVersionCheckTest {

    private static final String LOCATIONS = "classpath:db/migration";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    private static DataSource freshDatabase(String name) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Flyway flyway(DataSource db, String target) {
        return Flyway.configure().dataSource(db).locations(LOCATIONS).target(target).load();
    }

    @Test
    void aSchemaOneMigrationBehindStopsTheRunWithTheMissingVersion() throws SQLException {
        DataSource db = freshDatabase("behind");
        MigrationInfo[] all = flyway(db, "latest").info().all();
        String latest = all[all.length - 1].getVersion().getVersion();
        String previous = all[all.length - 2].getVersion().getVersion();
        flyway(db, previous).migrate();

        // Bug: with Flyway disabled and no check, the run starts and only fails (or silently
        // drops data) when it first touches the newer column.
        assertThatThrownBy(() -> new SchemaVersionCheck(db, LOCATIONS).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not migrated")
                .hasMessageContaining(latest);
    }

    @Test
    void anUpToDateSchemaLetsTheRunStart() throws SQLException {
        DataSource db = freshDatabase("current");
        flyway(db, "latest").migrate();

        // Bug: a check that also fails on a current schema blocks every scheduled crawl.
        assertThatCode(() -> new SchemaVersionCheck(db, LOCATIONS).verify()).doesNotThrowAnyException();
    }

    @Test
    void anEmptyDatabaseStopsTheRun() throws SQLException {
        DataSource db = freshDatabase("empty");

        // Bug: pointing the crawl at a fresh database (a new Supabase project before the
        // desktop app has run) starts a crawl that fails on every insert.
        assertThatThrownBy(() -> new SchemaVersionCheck(db, LOCATIONS).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not migrated");
    }
}
