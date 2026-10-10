package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.Job;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The per-user feed: active jobs minus the ones that user ignored, paged and counted alike. */
@Import(JobPersistenceAdapter.class)
class JobPersistenceAdapterFeedTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final int PAGE_SIZE = 20;

    @Autowired JobPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;
    UUID userId;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
        userId = insertUser();
    }

    @Test
    void the_feed_skips_ignored_jobs_and_inactive_ones() {
        UUID kept = jobs.insert("Kept", "k", NOW);
        UUID ignored = jobs.insert("Ignored", "i", NOW.minusSeconds(60));
        jobs.insert("Closed", "c", NOW, false, null);
        ignore(userId, ignored);

        List<UUID> feed = adapter.findActiveNotIgnoredBy(userId, 0, PAGE_SIZE).stream()
                .map(Job::id).toList();

        assertThat(feed).containsExactly(kept);
    }

    @Test
    void ignoring_an_inactive_job_does_not_shrink_the_count() {
        jobs.insert("Kept", "k", NOW);
        jobs.insert("Also kept", "a", NOW);
        UUID ignoredActive = jobs.insert("Ignored", "i", NOW);
        UUID ignoredClosed = jobs.insert("Ignored and closed", "c", NOW, false, null);
        ignore(userId, ignoredActive);
        ignore(userId, ignoredClosed);

        assertThat(adapter.countActiveNotIgnoredBy(userId)).isEqualTo(2);
    }

    @Test
    void another_users_ignores_do_not_hide_anything() {
        UUID job = jobs.insert("Kept", "k", NOW);
        ignore(insertUser(), job);

        assertThat(adapter.countActiveNotIgnoredBy(userId)).isEqualTo(1);
        assertThat(adapter.findActiveNotIgnoredBy(userId, 0, PAGE_SIZE)).extracting(Job::id)
                .containsExactly(job);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }

    private void ignore(UUID user, UUID job) {
        jdbc.update("INSERT INTO ignored_jobs (user_id, job_id) VALUES (?, ?)", user, job);
    }
}
