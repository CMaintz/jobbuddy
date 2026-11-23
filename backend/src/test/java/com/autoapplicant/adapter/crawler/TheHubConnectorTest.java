package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/**
 * Fixtures are real thehub.io pages: two job cards from the listing (plus its nav and a
 * tracked share link) and one detail page cut down to its JSON-LD block.
 */
class TheHubConnectorTest {

    private static final String JOB_URL = "https://thehub.io/jobs/6aa95a32eb03374734bd1fe2";

    private final TheHubConnector connector = new TheHubConnector();

    @Test
    void collects_job_links_without_the_listing_itself_or_tracking_duplicates() {
        assertThat(connector.extractJobLinks(CrawlerFixtures.html("thehub/listing.html")))
                .containsExactly(JOB_URL, "https://thehub.io/jobs/6a23669b553665162a8fae34");
    }

    @Test
    void maps_a_detail_page_from_its_json_ld() {
        RawJobData raw = connector.toRawJobData(CrawlerFixtures.html("thehub/detail.html"), JOB_URL);

        assertThat(raw.source()).isEqualTo(JobSource.THE_HUB);
        assertThat(raw.sourceJobId()).isEqualTo("6aa95a32eb03374734bd1fe2");
        assertThat(raw.url()).isEqualTo(JOB_URL);
        assertThat(raw.rawJson()).contains("\"JobPosting\"");
        assertThat(raw.rawHtml()).contains(
                "Title: Account Executive (Netherlands) - Help us build the Dutch market!\n",
                "Company: proprty.ai\n",
                "Company URL: https://proprty.ai\n",
                "Location: Amsterdam, Netherlands\n",
                "Date Posted: 2026-09-15T14:46:10.092Z\n",
                "Valid Through: 2026-11-28\n");
    }

    @Test
    void falls_back_to_page_html_when_the_json_ld_is_malformed() {
        String page = "<html><head><script type=\"application/ld+json\">{\"@type\": \"JobPosting\",</script>"
                + "</head><body><h1>Backend Engineer</h1></body></html>";

        RawJobData raw = connector.toRawJobData(Jsoup.parse(page), JOB_URL);

        assertThat(raw.rawJson()).isNull();
        assertThat(raw.rawHtml()).contains("<h1>Backend Engineer</h1>");
    }
}
