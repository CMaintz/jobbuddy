package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.EnrichmentStatus;
import com.autoapplicant.domain.job.Job;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The enrichment sweep's queue: what it picks up, in which order, and what it leaves alone. */
@Import(JobPersistenceAdapter.class)
class JobPersistenceAdapterEnrichmentQueueTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant RETRY_BEFORE = NOW.minus(Duration.ofHours(6));
    private static final int MAX_ATTEMPTS = 4;
    private static final int BATCH = 10;

    @Autowired JobPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
    }

    @Test
    void picks_pending_jobs_never_tried_first_then_the_longest_waiting() {
        UUID triedLongAgo = job("PENDING", 1, RETRY_BEFORE.minus(Duration.ofDays(1)));
        UUID neverTried = job("PENDING", 0, null);
        job("PENDING", 1, NOW.minus(Duration.ofHours(1)));
        job("PENDING", 1, RETRY_BEFORE);
        job("PENDING", MAX_ATTEMPTS, RETRY_BEFORE.minus(Duration.ofDays(1)));
        job("ENRICHED", 1, RETRY_BEFORE.minus(Duration.ofDays(1)));
        job("FAILED", MAX_ATTEMPTS, RETRY_BEFORE.minus(Duration.ofDays(1)));

        assertThat(adapter.findForEnrichment(BATCH, MAX_ATTEMPTS, RETRY_BEFORE))
                .extracting(Job::id)
                .containsExactly(neverTried, triedLongAgo);
    }

    @Test
    void the_batch_size_caps_the_queue() {
        UUID first = job("PENDING", 0, null);
        job("PENDING", 1, RETRY_BEFORE.minus(Duration.ofDays(1)));

        assertThat(adapter.findForEnrichment(1, MAX_ATTEMPTS, RETRY_BEFORE))
                .extracting(Job::id)
                .containsExactly(first);
    }

    @Test
    void counts_jobs_per_enrichment_status() {
        job("PENDING", 0, null);
        job("PENDING", 1, NOW);
        job("ENRICHED", 1, NOW);
        job("FAILED", MAX_ATTEMPTS, NOW);

        Map<EnrichmentStatus, Long> counts = adapter.countByEnrichmentStatus();

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(
                EnrichmentStatus.PENDING, 2L,
                EnrichmentStatus.ENRICHED, 1L,
                EnrichmentStatus.FAILED, 1L));
    }

    private UUID job(String status, int attempts, Instant lastAttemptAt) {
        UUID id = jobs.insert("Developer", "d", NOW);
        jobs.set(id, "enrichment_status", status);
        jobs.set(id, "enrichment_attempts", attempts);
        if (lastAttemptAt != null) {
            jobs.set(id, "enrichment_last_attempt_at", lastAttemptAt);
        }
        return id;
    }
}
