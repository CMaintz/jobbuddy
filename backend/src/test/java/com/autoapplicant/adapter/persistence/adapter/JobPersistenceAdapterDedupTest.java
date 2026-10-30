package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * How the crawler recognises a posting it already holds: by content fingerprint across sources,
 * and by its source's own id when a connector only reports that it saw the posting again.
 */
@Import(JobPersistenceAdapter.class)
class JobPersistenceAdapterDedupTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final long FINGERPRINT = 42L;
    private static final long OTHER_FINGERPRINT = 43L;
    private static final String SOURCE_JOB_ID = "ext-123";

    @Autowired JobPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
    }

    @Test
    void the_duplicate_is_the_oldest_active_posting_with_the_fingerprint_other_than_itself() {
        UUID self = fingerprinted(FINGERPRINT, true, NOW.minusSeconds(400));
        fingerprinted(FINGERPRINT, false, NOW.minusSeconds(300));
        UUID original = fingerprinted(FINGERPRINT, true, NOW.minusSeconds(200));
        fingerprinted(FINGERPRINT, true, NOW.minusSeconds(100));
        fingerprinted(OTHER_FINGERPRINT, true, NOW.minusSeconds(500));

        Optional<Job> duplicate = adapter.findActiveDuplicateByFingerprint(FINGERPRINT, self);

        assertThat(duplicate).map(Job::id).contains(original);
    }

    @Test
    void a_posting_alone_with_its_fingerprint_has_no_duplicate() {
        UUID self = fingerprinted(FINGERPRINT, true, NOW);
        fingerprinted(FINGERPRINT, false, NOW.minusSeconds(100));

        assertThat(adapter.findActiveDuplicateByFingerprint(FINGERPRINT, self)).isEmpty();
    }

    @Test
    void marking_seen_touches_only_the_posting_from_that_source() {
        UUID jobindex = withSourceId("JOBINDEX", SOURCE_JOB_ID);
        UUID linkedin = withSourceId("LINKEDIN", SOURCE_JOB_ID);
        UUID otherId = withSourceId("JOBINDEX", "ext-999");

        boolean marked = adapter.markSeenBySourceJobId(JobSource.JOBINDEX, SOURCE_JOB_ID, NOW);

        assertThat(marked).isTrue();
        assertThat(lastSeenAt(jobindex)).isEqualTo(NOW);
        assertThat(lastSeenAt(linkedin)).isNull();
        assertThat(lastSeenAt(otherId)).isNull();
    }

    @Test
    void marking_an_unknown_posting_seen_reports_that_nothing_matched() {
        withSourceId("LINKEDIN", SOURCE_JOB_ID);

        assertThat(adapter.markSeenBySourceJobId(JobSource.JOBINDEX, SOURCE_JOB_ID, NOW)).isFalse();
    }

    private UUID fingerprinted(long fingerprint, boolean active, Instant createdAt) {
        UUID id = jobs.insert("Developer", "d", NOW, active, null);
        jobs.set(id, "content_fingerprint", fingerprint);
        // Every row inserted in the test transaction would otherwise share now() as created_at.
        jobs.set(id, "created_at", createdAt);
        return id;
    }

    private UUID withSourceId(String source, String sourceJobId) {
        UUID id = jobs.insert("Developer", "d", NOW);
        jobs.set(id, "source", source);
        jobs.set(id, "source_job_id", sourceJobId);
        return id;
    }

    private Instant lastSeenAt(UUID id) {
        Timestamp seen = jdbc.queryForObject(
                "SELECT last_seen_at FROM jobs WHERE id = ?", Timestamp.class, id);
        return seen == null ? null : seen.toInstant();
    }
}
