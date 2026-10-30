package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Fixture is a trimmed real response from the Trustpilot board on boards-api.greenhouse.io. */
class GreenhouseConnectorTest {

    private final GreenhouseConnector connector = new GreenhouseConnector(null);

    @Test
    void maps_a_board_job_to_a_company_scoped_record() {
        JsonNode job = CrawlerFixtures.json("greenhouse/jobs.json").path("jobs").get(0);

        RawJobData raw = connector.toRawJobData("trustpilot", job);

        assertThat(raw.source()).isEqualTo(JobSource.GREENHOUSE);
        assertThat(raw.sourceJobId()).isEqualTo("gh-trustpilot-7588014");
        assertThat(raw.url())
                .isEqualTo("https://corporate.trustpilot.com/careers/job/7588014?gh_jid=7588014");
        assertThat(raw.rawHtml()).contains(
                "Title: Account Executive - Belgium market (Flemish speaking)\n",
                "Company: trustpilot\n",
                "Location: Amsterdam\n",
                "Department: New Biz\n",
                "Updated: 2026-09-15T01:38:19-04:00\n",
                "At Trustpilot");
    }

    @Test
    void skips_a_job_without_an_absolute_url() {
        JsonNode job = CrawlerFixtures.parseJson("{\"id\": 7588014, \"title\": \"Account Executive\"}");

        assertThat(connector.toRawJobData("trustpilot", job)).isNull();
    }
}
