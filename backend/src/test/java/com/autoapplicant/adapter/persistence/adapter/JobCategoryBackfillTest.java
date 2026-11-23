package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ingestion only refreshes a known posting's last-seen time and enrichment only fills an empty
 * category, so without this pass a GRC job filed under IT before the rules changed stays there,
 * hidden by the hard industry filter until it expires.
 */
@Import(JobCategoryBackfill.class)
class JobCategoryBackfillTest extends PostgresIntegrationTest {

    @Autowired JobCategoryBackfill backfill;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;

    @BeforeEach
    void freshMarker() {
        // A full-context test may already have run the pass against this shared database.
        jdbc.update("DELETE FROM job_category_backfill");
        jobs = new JobRows(jdbc);
    }

    @Test
    void active_misfiled_grc_jobs_move_and_everything_else_stays() {
        UUID misfiled = jobs.insert("Informationssikkerhedskoordinator", null, Instant.now(), true, "SOFTWARE_IT");
        UUID uncategorised = jobs.insert("Konsulent",
                "Du driver kommunens arbejde med informationssikkerhed og NIS2.", Instant.now(), true, "OTHER");
        UUID lawyer = jobs.insert("Jurist med speciale i GDPR", null, Instant.now(), true, "LEGAL");
        UUID expired = jobs.insert("Informationssikkerhedskoordinator", null, Instant.now(), false, "SOFTWARE_IT");

        assertThat(backfill.run()).isEqualTo(2);

        assertThat(category(misfiled)).isEqualTo("GRC_COMPLIANCE");
        assertThat(category(uncategorised)).isEqualTo("GRC_COMPLIANCE");
        assertThat(category(lawyer)).isEqualTo("LEGAL");
        assertThat(category(expired)).isEqualTo("SOFTWARE_IT");
    }

    @Test
    void a_rules_version_runs_once() {
        backfill.run();
        UUID later = jobs.insert("Informationssikkerhedskoordinator", null, Instant.now(), true, "SOFTWARE_IT");

        assertThat(backfill.run()).isEqualTo(-1);
        assertThat(category(later)).isEqualTo("SOFTWARE_IT");
    }

    private String category(UUID job) {
        return jdbc.queryForObject("SELECT job_category FROM jobs WHERE id = ?", String.class, job);
    }
}
