package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Fixtures are hand-built: the jobs API needs a JWT minted by the career site, so neither page
 * can be fetched anonymously. They follow the keys the connector reads.
 */
class CornerstoneConnectorTest {

    private static final int SITE_ID = 17;

    private final CornerstoneConnector connector = new CornerstoneConnector(null);
    private final JsonNode requisitions =
            CrawlerFixtures.json("cornerstone/jobs.json").path("data").path("requisitions");

    @Test
    void extracts_the_bearer_token_and_api_host_from_the_career_site() {
        String home = CrawlerFixtures.read("cornerstone/home.html");

        assertThat(connector.extractGroup(CornerstoneConnector.JWT_PATTERN, home))
                .isEqualTo("eyJtesttoken");
        assertThat(connector.extractGroup(CornerstoneConnector.API_HOST_PATTERN, home))
                .isEqualTo("https://eu-fra.api.csod.com/");
    }

    @Test
    void maps_a_requisition_to_a_tenant_scoped_record() {
        RawJobData raw = connector.toRawJobData(requisitions.get(0), "tdc", SITE_ID);

        assertThat(raw.source()).isEqualTo(JobSource.CORNERSTONE_ONDEMAND);
        assertThat(raw.sourceJobId()).isEqualTo("csod-tdc-4321");
        assertThat(raw.url())
                .isEqualTo("https://tdc.csod.com/ux/ats/careersite/17/app?type=job&id=4321&c=tdc");
        assertThat(raw.rawHtml()).contains(
                "Title: Network Engineer\n",
                "Company: tdc\n",
                "Req ID: REQ-4321\n",
                "Location: Copenhagen, Denmark\n",
                "Posted: 2026-10-20T00:00:00Z\n",
                "Description: You will design and operate our core IP network.\n");
    }

    @Test
    void falls_back_to_id_and_created_date_when_req_fields_are_absent() {
        RawJobData raw = connector.toRawJobData(requisitions.get(1), "tdc", SITE_ID);

        assertThat(raw.sourceJobId()).isEqualTo("csod-tdc-4322");
        assertThat(raw.rawHtml()).contains(
                "Req ID: 4322\n", "Location: Denmark\n", "Posted: 2026-10-18T00:00:00Z\n");
    }

    @Test
    void skips_a_requisition_without_any_id() {
        JsonNode req = CrawlerFixtures.parseJson("{\"title\": \"Network Engineer\"}");

        assertThat(connector.toRawJobData(req, "tdc", SITE_ID)).isNull();
    }
}
