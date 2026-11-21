package com.autoapplicant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.crawler.CrawlerOrchestrator;
import com.autoapplicant.adapter.crawler.EnrichmentSweepScheduler;
import com.autoapplicant.adapter.crawler.LinkedInCrawlScheduler;
import com.autoapplicant.adapter.notification.DueReminderScheduler;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import com.autoapplicant.usecase.job.JobExpiryService;
import com.autoapplicant.usecase.job.JobUrlCheckService;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The whole app under the desktop profile, against a real Postgres, with no Firebase credentials
 * anywhere: it has to start, own exactly one local user, and run only the desktop's schedules.
 */
@SpringBootTest(
        properties = {
            "app.desktop.token=" + DesktopProfileBootTest.TOKEN,
            // The profile's default is the user's real local app data.
            "app.data-dir=build/desktop-boot-data",
            // Keep the workers from starting real work while the test runs.
            "app.enrichment.sweep.initial-delay-ms=3600000",
            "app.embedding.sweep.initial-delay-ms=3600000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
@Testcontainers(disabledWithoutDocker = true)
class DesktopProfileBootTest {

    private static final int PARALLEL_REQUESTS = 8;
    static final String TOKEN = "boot-test-token-0123456789abcdef0123456789";

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired ProvisionLocalUserUseCase localUser;
    @Autowired UserRepositoryPort users;
    @Autowired ScheduledTaskHolder schedules;

    @Test
    void starts_without_any_firebase_bean() {
        assertThat(context.getBeanProvider(FirebaseAuth.class).getIfAvailable()).isNull();
    }

    @Test
    void the_local_user_exists_from_startup_and_is_who_the_token_signs_in_as() throws Exception {
        User provisioned = users.findByEmail(LocalUser.EMAIL).orElseThrow();

        mvc.perform(get("/api/v1/auth/me").header("X-JobBuddy-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(provisioned.id().toString()))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void provisioning_again_on_a_later_start_reuses_the_same_user() {
        User first = localUser.provisionLocalUser();
        User again = localUser.provisionLocalUser();

        assertThat(again.id()).isEqualTo(first.id());
    }

    @Test
    void parallel_requests_right_after_the_account_is_deleted_all_get_the_same_new_user()
            throws Exception {
        users.deleteById(localUser.provisionLocalUser().id());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService requests = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
        try {
            List<Future<User>> provisioned = new ArrayList<>();
            for (int i = 0; i < PARALLEL_REQUESTS; i++) {
                provisioned.add(requests.submit(() -> {
                    start.await();
                    return localUser.provisionLocalUser();
                }));
            }
            start.countDown();
            Set<UUID> ids = new HashSet<>();
            for (Future<User> user : provisioned) {
                ids.add(user.get(30, TimeUnit.SECONDS).id());
            }
            assertThat(ids).hasSize(1);
        } finally {
            requests.shutdownNow();
        }
    }

    @Test
    void runs_what_needs_this_pc_and_leaves_the_shared_jobs_to_the_crawl_workflow() {
        List<String> scheduled = scheduledTasks();

        // Needs the user's Claude login or residential IP, or is per-user: runs here.
        assertThat(scheduled)
                .anyMatch(isTaskOf(EnrichmentSweepScheduler.class))
                .anyMatch(isTaskOf(DueReminderScheduler.class))
                .anyMatch(isTaskOf(LinkedInCrawlScheduler.class));
        // The GitHub Actions crawl run owns these; running them here too would race it on the
        // same database.
        assertThat(scheduled)
                .noneMatch(isTaskOf(CrawlerOrchestrator.class))
                .noneMatch(isTaskOf(JobUrlCheckService.class))
                .noneMatch(isTaskOf(JobExpiryService.class));
    }

    // A task describes itself by its method's qualified name, through any wrapper Spring adds.
    private List<String> scheduledTasks() {
        return schedules.getScheduledTasks().stream()
                .map(task -> task.getTask().toString())
                .toList();
    }

    private static Predicate<String> isTaskOf(Class<?> scheduler) {
        return task -> task.startsWith(scheduler.getName() + ".");
    }

    /** The fixed address the desktop profile's single user is stored under. */
    private static final class LocalUser {
        static final String EMAIL = "local@jobbuddy.invalid";
    }
}
