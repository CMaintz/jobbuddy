package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The two ways a posting closes on its own: no crawler has seen it lately, or its deadline passed. */
@Import(JobPersistenceAdapter.class)
class JobPersistenceAdapterExpiryTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(3));
    private static final Instant BEFORE_CUTOFF = CUTOFF.minus(Duration.ofDays(1));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    @Autowired JobPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
    }

    @Test
    void only_active_crawled_postings_unseen_since_the_cutoff_are_stale() {
        UUID stale = seen(BEFORE_CUTOFF, true);
        UUID recent = seen(NOW, true);
        UUID neverSeen = jobs.insert("Developer", "d", NOW);
        seen(BEFORE_CUTOFF, false);
        UUID manual = seen(BEFORE_CUTOFF, true);
        jobs.set(manual, "source", "MANUAL");

        assertThat(adapter.findStaleActiveJobIds(CUTOFF)).containsExactly(stale);
        assertThat(adapter.deactivateStaleJobs(CUTOFF)).isEqualTo(1);

        assertThat(isActive(stale)).isFalse();
        assertThat(isActive(recent)).isTrue();
        assertThat(isActive(neverSeen)).isTrue();
        assertThat(isActive(manual)).isTrue();
    }

    @Test
    void a_deadline_closes_a_posting_only_once_it_lies_strictly_before_the_date() {
        UUID expired = withDeadline(TODAY.minusDays(1), true);
        UUID dueToday = withDeadline(TODAY, true);
        UUID noDeadline = jobs.insert("Developer", "d", NOW);
        withDeadline(TODAY.minusDays(1), false);

        assertThat(adapter.deactivateDeadlineExpiredJobs(TODAY)).containsExactly(expired);

        assertThat(isActive(expired)).isFalse();
        assertThat(isActive(dueToday)).isTrue();
        assertThat(isActive(noDeadline)).isTrue();
    }

    private UUID seen(Instant lastSeenAt, boolean active) {
        UUID id = jobs.insert("Developer", "d", NOW, active, null);
        jobs.set(id, "last_seen_at", lastSeenAt);
        return id;
    }

    private UUID withDeadline(LocalDate deadline, boolean active) {
        UUID id = jobs.insert("Developer", "d", NOW, active, null);
        jobs.set(id, "application_deadline", deadline);
        return id;
    }

    private boolean isActive(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT is_active FROM jobs WHERE id = ?", Boolean.class, id));
    }
}
