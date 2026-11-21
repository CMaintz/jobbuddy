package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.EnrichmentScope;
import com.autoapplicant.domain.job.Job;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Enrichment spends the user's own CLI quota, one posting at a time, so the queue must hold
 * only postings she could still act on. Embedding costs less but still pays for dead postings.
 */
@Import(JobPersistenceAdapter.class)
class EnrichmentQueueScopeTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final int MAX_ATTEMPTS = 4;

    @Autowired JobPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;
    UUID user;
    EnrichmentScope scope = EnrichmentScope.everything();

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
        user = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", user, user + "@example.com");
    }

    private List<UUID> enrichmentQueue() {
        return adapter.findForEnrichment(100, MAX_ATTEMPTS, NOW, scope).stream().map(Job::id).toList();
    }

    private List<UUID> embeddingQueue() {
        return adapter.findForEmbedding(100, MAX_ATTEMPTS, NOW, false).stream().map(Job::id).toList();
    }

    private UUID withApplication(String status) {
        UUID job = jobs.insert("Developer " + status, "d", NOW);
        jdbc.update("INSERT INTO applications (user_id, job_id, status) VALUES (?, ?, ?)", user, job, status);
        return job;
    }

    private UUID pastDeadline() {
        UUID job = jobs.insert("Expired", "d", NOW);
        jdbc.update("UPDATE jobs SET application_deadline = ? WHERE id = ?",
                Date.valueOf(LocalDate.now().minusDays(1)), job);
        return job;
    }

    @Test
    void closedAndExpiredPostingsAreNotEnriched() {
        // Bug: the queue ignores is_active and the deadline, so quota goes on postings that
        // are gone and that she will never see in her feed.
        UUID open = jobs.insert("Open", "d", NOW);
        UUID closed = jobs.insert("Closed", "d", NOW, false, null);
        UUID expired = pastDeadline();

        assertThat(enrichmentQueue()).contains(open).doesNotContain(closed, expired);
    }

    @Test
    void ignoredPostingsAreNotEnriched() {
        // Bug: a posting she dismissed is still enriched, at the same cost as a live lead.
        UUID ignored = jobs.insert("Ignored", "d", NOW);
        jdbc.update("INSERT INTO ignored_jobs (user_id, job_id) VALUES (?, ?)", user, ignored);

        assertThat(enrichmentQueue()).doesNotContain(ignored);
    }

    @Test
    void postingsSheAppliedToOrClosedAreNotEnrichedButOnesSheIsWorkingOnAre() {
        // Bug: treating any application row as done skips the postings she saved or is
        // preparing, which are the ones enrichment matters most for; treating none as done
        // spends quota on postings already applied to or archived.
        UUID saved = withApplication("SAVED");
        UUID preparing = withApplication("PREPARING");
        UUID applied = withApplication("APPLIED");
        UUID interview = withApplication("INTERVIEW");
        UUID rejected = withApplication("REJECTED");
        UUID archived = withApplication("ARCHIVED");

        assertThat(enrichmentQueue()).contains(saved, preparing)
                .doesNotContain(applied, interview, rejected, archived);
    }

    @Test
    void closedAndExpiredPostingsAreNotEmbedded() {
        // Bug: the embedding queue pays for vectors of postings no search will ever return.
        UUID open = jobs.insert("Open", "d", NOW);
        UUID closed = jobs.insert("Closed", "d", NOW, false, null);
        UUID expired = pastDeadline();

        assertThat(embeddingQueue()).contains(open).doesNotContain(closed, expired);
    }

    @Test
    void aCategoryEveryoneSkipsIsNotEnriched() {
        // Bug: the skip list reaches the feed but not the queue, so quota still goes on
        // postings she will never be shown.
        UUID sales = jobs.insert("Sales", "s", NOW, true, "SALES");
        UUID software = jobs.insert("Software", "s", NOW, true, "SOFTWARE_IT");
        UUID uncategorised = jobs.insert("Uncategorised", "u", NOW);
        scope = new EnrichmentScope(Set.of("SALES"));

        assertThat(enrichmentQueue()).contains(software, uncategorised).doesNotContain(sales);
    }
}
