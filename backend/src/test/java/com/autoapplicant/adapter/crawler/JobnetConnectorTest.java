package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Fixtures are hand-built from the fields the connector reads: the jobnet.dk BFF answers an
 * anonymous request with 401, so no real sample could be saved.
 */
class JobnetConnectorTest {

    private static final String AD_ID = "b3f1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

    private final JobnetConnector connector = new JobnetConnector();
    private final JsonNode searchAd = CrawlerFixtures.json("jobnet/search.json").path("jobAds").get(0);

    @Test
    void prefers_the_detail_response_over_the_search_card() throws Exception {
        RawJobData raw = connector.toRawJobData(AD_ID, searchAd, CrawlerFixtures.read("jobnet/detail.json"));

        assertThat(raw.source()).isEqualTo(JobSource.JOBNET);
        assertThat(raw.sourceJobId()).isEqualTo(AD_ID);
        assertThat(raw.url()).isEqualTo("https://job.jobnet.dk/CV/FindWork/Details/" + AD_ID);
        assertThat(raw.companyName()).isEqualTo("Region Midtjylland, Digitalisering");
        assertThat(raw.location()).isEqualTo("Viborg");
        assertThat(raw.applicationDeadline()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(raw.postedAt()).isEqualTo(Instant.parse("2026-10-27T08:30:00Z"));
        assertThat(raw.rawCategories()).containsExactly("Systemudvikler", "Softwareudvikler");
        assertThat(raw.rawHtml()).contains(
                "<h1>Systemudvikler til digitale borgerløsninger</h1>", "Ansøgningsfrist: 2026-11-23");
        assertThat(raw.shortDescription()).startsWith("Vi søger en systemudvikler");
    }

    @Test
    void falls_back_to_the_search_card_when_the_detail_lacks_fields() throws Exception {
        RawJobData raw = connector.toRawJobData(AD_ID, searchAd, "{}");

        assertThat(raw.companyName()).isEqualTo("Region Midtjylland");
        assertThat(raw.location()).isEqualTo("Aarhus");
        assertThat(raw.applicationDeadline()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(raw.postedAt()).isEqualTo(Instant.parse("2026-10-27T00:00:00Z"));
        assertThat(raw.rawHtml()).contains("<h1>Systemudvikler</h1>");
        assertThat(raw.shortDescription()).isNull();
    }
}
