package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Fixture is hand-built from the Careerjet search API's documented response shape. */
class CareerjetConnectorTest {

    private final CareerjetConnector connector = new CareerjetConnector(null);
    private final JsonNode jobs = CrawlerFixtures.json("careerjet/search.json").path("jobs");

    @Test
    void maps_a_search_hit_to_a_record_keyed_on_its_url() {
        RawJobData job = connector.toRawJobData(jobs.get(0));

        assertThat(job.source()).isEqualTo(JobSource.CAREERJET);
        assertThat(job.url())
                .isEqualTo("https://jobviewtrack.com/en-gb/job-4a1c0e1f4b1d4207/7d4e3b0f2c1a9e8b.html");
        assertThat(job.sourceJobId()).isEqualTo("7d4e3b0f2c1a9e8b.html");
        assertThat(job.rawHtml()).contains(
                "Title: Backend Developer\n",
                "Company: Nordlys Software ApS\n",
                "Location: Aarhus\n",
                "Salary: DKK 50,000 - 60,000 per month\n",
                "Date Posted: Wed, 28 Oct 2026 08:12:45 GMT\n");
    }

    @Test
    void skips_a_hit_without_a_url() {
        assertThat(connector.toRawJobData(jobs.get(1))).isNull();
    }
}
