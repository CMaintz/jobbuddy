package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.usecase.job.BatchedEnrichment;
import com.autoapplicant.usecase.job.EnrichmentSweepService;
import com.autoapplicant.usecase.job.EnrichmentSweepSettings;
import com.autoapplicant.usecase.job.JobEnrichmentService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Enrichment holds a posting for the length of an AI call while the crawl, in another process,
 * expires and probes the same rows. What enrichment writes back must be only what it produced.
 */
@Import(JobPersistenceAdapter.class)
class EnrichmentWriteBackTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant PROBED_AT = Instant.parse("2026-09-01T13:00:00Z");
    private static final String TEXT = "Build our payments API\nWe offer free fruit";

    @Autowired JobPersistenceAdapter jobsAdapter;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    JobRows jobs;
    final JobEnrichmentService enrichJob = Mockito.mock(JobEnrichmentService.class);

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
    }

    /**
     * Runs one sweep in which {@code meanwhile} happens while the AI call is in flight, as a
     * separate process would do it: straight to the table, outside this sweep's loaded state.
     */
    private void enrichWhile(Runnable meanwhile, UnaryOperator<Job> enrichment) {
        when(enrichJob.enrich(any())).thenAnswer(inv -> {
            Job read = inv.getArgument(0);
            entityManager.flush();
            entityManager.clear();
            meanwhile.run();
            return CompletableFuture.completedFuture(enrichment.apply(read));
        });
        new EnrichmentSweepService(jobsAdapter, enrichJob, Mockito.mock(BatchedEnrichment.class),
                Mockito.mock(PreferencesRepositoryPort.class),
                new EnrichmentSweepSettings(4, Duration.ofHours(6), Duration.ofMinutes(1), 0, 1, 1)).sweep(10);
        entityManager.flush();
        entityManager.clear();
    }

    private static Job summarised(Job read) {
        return read.toBuilder().aiSummary("Payments backend role").build();
    }

    private <T> T column(UUID id, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM jobs WHERE id = ?", type, id);
    }

    @Test
    void aPostingExpiredDuringEnrichmentStaysExpired() {
        // Bug: enrichment saves the whole row it read before the AI call, so is_active goes
        // back to true and a posting the crawl just retired reappears in the feed.
        UUID id = jobs.insert("Backend developer", TEXT, NOW);

        enrichWhile(() -> jdbc.update("UPDATE jobs SET is_active = false WHERE id = ?", id),
                EnrichmentWriteBackTest::summarised);

        assertThat(column(id, "is_active", Boolean.class)).isFalse();
        assertThat(column(id, "ai_summary", String.class)).isEqualTo("Payments backend role");
        assertThat(column(id, "enrichment_status", String.class)).isEqualTo("ENRICHED");
    }

    @Test
    void aLivenessRefreshDuringEnrichmentIsKept() {
        // Bug: the stale last_seen_at is written back, undoing the probe, and expiry later
        // retires a posting that was confirmed live.
        UUID id = jobs.insert("Backend developer", TEXT, NOW);

        enrichWhile(() -> jdbc.update("UPDATE jobs SET last_seen_at = ? WHERE id = ?",
                Timestamp.from(PROBED_AT), id), EnrichmentWriteBackTest::summarised);

        assertThat(column(id, "last_seen_at", Timestamp.class).toInstant()).isEqualTo(PROBED_AT);
    }

    @Test
    void aDeadlineTheCrawlPublishesDuringEnrichmentIsKept() {
        // Bug: the crawl refreshes the deadline a source published after first crawl, and the
        // gap-filled deadline enrichment derived from its stale read (here none) overwrites it.
        UUID id = jobs.insert("Backend developer", TEXT, NOW);

        enrichWhile(() -> jdbc.update("UPDATE jobs SET application_deadline = ? WHERE id = ?",
                java.sql.Date.valueOf("2026-09-30"), id), EnrichmentWriteBackTest::summarised);

        assertThat(column(id, "application_deadline", java.sql.Date.class))
                .isEqualTo(java.sql.Date.valueOf("2026-09-30"));
    }

    @Test
    void enrichmentThatTrimsTheTextRequeuesAnEmbeddedPosting() {
        // Bug: the targeted write drops the embedding reset, so a posting embedded at crawl
        // time keeps the vector of the untrimmed text.
        UUID id = jobs.insert("Backend developer", TEXT, NOW);
        jdbc.update("UPDATE jobs SET embedding_status = 'EMBEDDED', embedding_attempts = 1 WHERE id = ?", id);

        enrichWhile(() -> { }, read -> summarised(read).toBuilder()
                .descriptionClean("Build our payments API").build());

        assertThat(column(id, "embedding_status", String.class)).isEqualTo("PENDING");
        assertThat(column(id, "embedding_attempts", Integer.class)).isZero();
        assertThat(column(id, "description_clean", String.class)).isEqualTo("Build our payments API");
    }

    @Test
    void enrichmentThatLeavesTheTextAloneKeepsTheEmbedding() {
        // Bug: the targeted write resets embedding state unconditionally and every enrichment
        // pays for the same vector again.
        UUID id = jobs.insert("Backend developer", TEXT, NOW);
        jdbc.update("UPDATE jobs SET embedding_status = 'EMBEDDED', embedding_attempts = 1 WHERE id = ?", id);

        enrichWhile(() -> { }, EnrichmentWriteBackTest::summarised);

        assertThat(column(id, "embedding_status", String.class)).isEqualTo("EMBEDDED");
    }

    @Test
    void theFailureThatReachesTheLimitGivesUpAndEarlierOnesDoNot() {
        // Bug: the attempt count in the targeted update is compared before it is incremented,
        // so a posting the model can never summarise costs one more call than the limit allows.
        UUID last = jobs.insert("Backend developer", TEXT, NOW);
        UUID first = jobs.insert("Frontend developer", TEXT, NOW);
        jdbc.update("UPDATE jobs SET enrichment_attempts = 3 WHERE id = ?", last);

        assertThat(jobsAdapter.markEnrichmentFailed(last, "timeout", 4)).isTrue();
        assertThat(jobsAdapter.markEnrichmentFailed(first, "timeout", 4)).isFalse();

        assertThat(column(last, "enrichment_status", String.class)).isEqualTo("FAILED");
        assertThat(column(first, "enrichment_status", String.class)).isEqualTo("PENDING");
        assertThat(column(first, "enrichment_attempts", Integer.class)).isEqualTo(1);
    }
}
