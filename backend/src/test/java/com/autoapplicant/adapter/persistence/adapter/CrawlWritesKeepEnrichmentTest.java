package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The crawl's writes to a posting (seen again, URL probed, embedding failed) run while the
 * desktop app may be enriching the same posting. Each must write its own columns only, or it
 * puts back the fields as they were before enrichment.
 *
 * <p>The posting is loaded first, so the crawl write starts from a copy older than the
 * enrichment that lands next, as it would when the two processes overlap.
 */
@Import(JobPersistenceAdapter.class)
class CrawlWritesKeepEnrichmentTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final String SUMMARY = "Payments backend role";

    @Autowired JobPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    JobRows jobs;
    UUID job;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
        job = jobs.insert("Backend developer", "Build our payments API", NOW);
        adapter.findById(job).orElseThrow();
        jdbc.update("UPDATE jobs SET ai_summary = ?, enrichment_status = 'ENRICHED' WHERE id = ?", SUMMARY, job);
    }

    private String column(String name) {
        entityManager.flush();
        entityManager.clear();
        return jdbc.queryForObject("SELECT " + name + "::text FROM jobs WHERE id = ?", String.class, job);
    }

    @Test
    void aRecrawlThatSeesThePostingAgainKeepsTheEnrichment() {
        // Bug: refreshLastSeen saves the whole row it read, so a re-crawl during enrichment wipes
        // the summary, skills and requirements just written.
        adapter.refreshLastSeen(job, NOW.plusSeconds(60), LocalDate.of(2026, 9, 30));

        assertThat(column("ai_summary")).isEqualTo(SUMMARY);
        assertThat(column("enrichment_status")).isEqualTo("ENRICHED");
        assertThat(column("application_deadline")).isEqualTo("2026-09-30");
    }

    @Test
    void aLiveUrlProbeKeepsTheEnrichment() {
        // Bug: markUrlAlive saves the whole row it read, undoing an enrichment that landed
        // between its read and its write.
        Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        adapter.markUrlAlive(job);

        assertThat(column("ai_summary")).isEqualTo(SUMMARY);
        assertThat(column("url_check_failures")).isEqualTo("0");
        // A live URL is what keeps expiry off a posting the crawl no longer revisits.
        entityManager.flush();
        Instant seen = jdbc.queryForObject("SELECT last_seen_at FROM jobs WHERE id = ?",
                java.sql.Timestamp.class, job).toInstant();
        assertThat(seen).isAfterOrEqualTo(before).isAfter(NOW);
    }

    @Test
    void anInconclusiveProbeKeepsTheEnrichment() {
        // Bug: the same whole-row save on the inconclusive path.
        adapter.markUrlCheckInconclusive(job);

        assertThat(column("ai_summary")).isEqualTo(SUMMARY);
    }

    @Test
    void aTakenDownProbeKeepsTheEnrichmentAndStillDeactivates() {
        // Bug: the whole-row save on the taken-down path wipes the enrichment; or the targeted
        // version loses the deactivation the threshold calls for.
        assertThat(adapter.markUrlTakenDown(job, 1)).isTrue();

        assertThat(column("ai_summary")).isEqualTo(SUMMARY);
        assertThat(column("is_active")).isEqualTo("false");
    }

    @Test
    void aProbeOfAPostingAlreadyClosedDoesNotReportRetiringIt() {
        // Bug: without the active guard a closed posting is "deactivated" again, and the probe
        // counts it among the postings it retired.
        jdbc.update("UPDATE jobs SET is_active = false WHERE id = ?", job);

        assertThat(adapter.markUrlTakenDown(job, 1)).isFalse();
    }

    @Test
    void aTakenDownProbeBelowTheThresholdOnlyCounts() {
        // Bug: the targeted version deactivates on the first soft failure.
        assertThat(adapter.markUrlTakenDown(job, 2)).isFalse();

        assertThat(column("is_active")).isEqualTo("true");
        assertThat(column("url_check_failures")).isEqualTo("1");
    }

    @Test
    void aFailedEmbeddingKeepsTheEnrichment() {
        // Bug: markEmbeddingFailed saves the whole row it read, so a failed embed during
        // enrichment wipes the summary.
        adapter.markEmbeddingFailed(job, "no vector returned", 4);

        assertThat(column("ai_summary")).isEqualTo(SUMMARY);
        assertThat(column("embedding_attempts")).isEqualTo("1");
    }

    @Test
    void theEmbeddingFailureThatReachesTheLimitGivesUp() {
        // Bug: the targeted version compares the attempt count before incrementing it.
        jdbc.update("UPDATE jobs SET embedding_attempts = 3 WHERE id = ?", job);

        assertThat(adapter.markEmbeddingFailed(job, "no vector returned", 4)).isTrue();
        assertThat(column("embedding_status")).isEqualTo("FAILED");
    }
}
