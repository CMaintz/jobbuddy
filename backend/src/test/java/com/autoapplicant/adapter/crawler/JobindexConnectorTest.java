package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.adapter.crawler.JobindexConnector.StashMeta;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/**
 * Fixtures are the real jobindex.dk RSS feed and search page, trimmed to two postings each;
 * the search page keeps only its {@code var Stash} blob.
 */
class JobindexConnectorTest {

    private static final String DETAIL_HTML = "<html>detail</html>";

    private final JobindexConnector connector = new JobindexConnector();

    @Test
    void maps_an_rss_item_with_its_link_categories_and_teaser() {
        Element item = CrawlerFixtures.rssItems("jobindex/feed.rss").get(0);
        String link = connector.extractRssLink(item);

        RawJobData raw = connector.toRawJobData(
                item, item.select("guid").text().trim(), link, DETAIL_HTML, null);

        assertThat(link).isEqualTo("https://www.jobindex.dk/vis-job/h1712286");
        assertThat(raw.source()).isEqualTo(JobSource.JOBINDEX);
        assertThat(raw.url()).isEqualTo(link);
        assertThat(raw.rawCategories()).containsExactly("Pædagog");
        assertThat(raw.shortDescription()).startsWith(
                "Vil du gøre en reel forskel for børn, som har brug for tydelige, trygge og stabile "
                        + "voksne omkring sig? Specialskolen Bramsnæsvig søger");
        assertThat(raw.applicationDeadline()).isNull();
        assertThat(raw.companyName()).isNull();
    }

    @Test
    void reads_deadline_company_and_first_date_from_the_stash() throws Exception {
        JsonNode results = connector.parseStashResults(CrawlerFixtures.read("jobindex/search.html"));

        StashMeta meta = JobindexConnector.toStashMeta(results.get(1));

        assertThat(results.get(1).path("tid").asText()).isEqualTo("h1711203");
        assertThat(meta.deadline()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(meta.companyName()).isEqualTo("Haderslev Kommune");
        assertThat(meta.companyHomeUrl()).isEqualTo("https://www.haderslev.dk/forside/");
        assertThat(meta.firstDate()).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
    }

    @Test
    void stores_no_deadline_for_an_asap_posting_despite_its_last_date() throws Exception {
        JsonNode results = connector.parseStashResults(CrawlerFixtures.read("jobindex/search.html"));

        StashMeta meta = JobindexConnector.toStashMeta(results.get(0));

        assertThat(meta.deadline()).isNull();
        assertThat(meta.companyName()).isEqualTo("Sinatur Hotel");
        assertThat(meta.area()).isEqualTo("Ebberup");
    }

    @Test
    void carries_stash_metadata_onto_the_record() throws Exception {
        Element item = CrawlerFixtures.rssItems("jobindex/feed.rss").get(0);
        JsonNode results = connector.parseStashResults(CrawlerFixtures.read("jobindex/search.html"));
        StashMeta meta = JobindexConnector.toStashMeta(results.get(1));

        RawJobData raw = connector.toRawJobData(item, "guid", "link", DETAIL_HTML, meta);

        assertThat(raw.applicationDeadline()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(raw.companyName()).isEqualTo("Haderslev Kommune");
        assertThat(raw.companyWebsiteUrl()).isEqualTo("https://www.haderslev.dk/forside/");
        assertThat(raw.postedAt()).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
    }

    @Test
    void finds_no_results_in_a_stash_without_a_search_response() throws Exception {
        String html = "<script>var Stash = {\"common\": {\"user\": null}};</script>";

        assertThat(connector.parseStashResults(html)).isNull();
    }

    @Test
    void rejects_a_page_without_a_stash_blob() {
        assertThatThrownBy(() -> connector.parseStashResults("<html><body>Maintenance</body></html>"))
                .isInstanceOf(IllegalStateException.class);
    }
}
