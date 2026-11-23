package com.autoapplicant.adapter.persistence;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for persistence tests against a real Postgres with pgvector (the image
 * docker-compose runs), migrated by Flyway exactly as production is. Skipped, not
 * failed, where no Docker daemon is reachable; CI always has one.
 *
 * <p>One container for the whole run rather than one per class: Spring caches the
 * context across classes with the same config, and a per-class container would leave
 * that cached context pointing at a stopped database.
 *
 * <p>The flip side is that every distinct test configuration (each {@code @Import} or
 * {@code @MockitoBean} set) is its own cached context with its own Hikari pool, and all of
 * them stay open against this one server. At Hikari's default of ten connections held per
 * pool, about ten such contexts exhaust Postgres's 100 and the next one fails at Flyway with
 * "too many clients already". A small pool that idles down to one keeps the total far below
 * that; no test here needs more than a couple of connections at once.
 */
@DataJpaTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    private static final DockerImageName PGVECTOR =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR);

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }
}
