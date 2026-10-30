package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Fixture is a trimmed real response from api.lever.co for the Veo board. */
class LeverConnectorTest {

    private final LeverConnector connector = new LeverConnector(null);

    @Test
    void maps_a_posting_with_its_categories_and_lists() {
        JsonNode posting = CrawlerFixtures.json("lever/postings.json").get(0);

        RawJobData raw = connector.toRawJobData("veo", posting);

        assertThat(raw.source()).isEqualTo(JobSource.LEVER);
        assertThat(raw.sourceJobId()).isEqualTo("lever-veo-7ec8c80d-37f2-488c-a45a-c5460bad5645");
        assertThat(raw.url())
                .isEqualTo("https://jobs.lever.co/veo/7ec8c80d-37f2-488c-a45a-c5460bad5645");
        assertThat(raw.rawHtml()).contains(
                "Title: Android Engineer\n",
                "Company: veo\n",
                "Location: Copenhagen Office\n",
                "Team: Engineering\n",
                "Employment Type: Full Time\n",
                "Description: Veo is an AI-powered sports camera",
                "What You Will Do:\nWrite high-quality, maintainable Kotlin code");
    }

    @Test
    void skips_a_posting_without_an_id() {
        JsonNode posting = CrawlerFixtures.parseJson(
                "{\"text\": \"Android Engineer\", \"hostedUrl\": \"https://jobs.lever.co/veo/x\"}");

        assertThat(connector.toRawJobData("veo", posting)).isNull();
    }
}
