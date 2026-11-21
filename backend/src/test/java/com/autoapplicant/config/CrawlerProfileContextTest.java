package com.autoapplicant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.cli.CrawlerCliRunner;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The crawler profile as the scheduled crawl runs it: against a database another process
 * migrates, with no Firebase project and no one to answer a schedule. The credentials path points
 * at a file that does not exist, so reading it at all fails the context.
 */
@SpringBootTest(properties = "app.firebase.credentials-path=does-not-exist/service-account.json")
@ActiveProfiles("crawler")
@Testcontainers(disabledWithoutDocker = true)
class CrawlerProfileContextTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            // The desktop app is the migrator; here the test plays that part.
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .load().migrate();
        }
    }

    /** Replaced so starting the context does not crawl the real job boards. */
    @MockitoBean CrawlerCliRunner runner;

    @Autowired ApplicationContext context;

    @Test
    void startsWithoutFirebaseCredentials() {
        // Bug: the profile builds the real Firebase app, so the crawl cannot boot without a
        // service-account file (a dummy one had to be generated in CI just to get past it).
        assertThat(context.getBean(FirebaseAuth.class)).isNotNull();
    }

    @Test
    void registersNoScheduledTasks() {
        // Bug: every @Scheduled bean ticks inside the crawl process. The LinkedIn plan cron
        // calls the generation provider, which a CI runner has no login for, and the sweeps
        // race the crawl's own drain.
        List<ScheduledTask> tasks = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .toList();

        assertThat(tasks).isEmpty();
    }

    @Test
    void doesNotMigrateTheSchema() {
        // Bug: two processes migrate one shared schema; a crawl built from a newer commit than
        // the desktop app would move the schema out from under it.
        assertThat(context.getBeanProvider(Flyway.class).getIfAvailable()).isNull();
    }
}
