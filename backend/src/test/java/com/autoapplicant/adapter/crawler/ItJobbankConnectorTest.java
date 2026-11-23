package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/** Fixture is the real it-jobbank.dk RSS feed, trimmed to two items. */
class ItJobbankConnectorTest {

    private final ItJobbankConnector connector = new ItJobbankConnector();

    @Test
    void maps_an_rss_item_with_its_link_categories_and_teaser() {
        Element item = CrawlerFixtures.rssItems("itjobbank/feed.rss").get(1);
        String link = connector.extractRssLink(item);

        RawJobData raw = connector.toRawJobData(
                item, item.select("guid").text().trim(), link, "<html>detail</html>");

        assertThat(link).isEqualTo("https://www.it-jobbank.dk/vis-job/h1712069");
        assertThat(raw.source()).isEqualTo(JobSource.IT_JOBBANK);
        assertThat(raw.url()).isEqualTo(link);
        assertThat(raw.rawCategories()).containsExactly("Forretningsudvikling", "Salg");
        assertThat(raw.shortDescription()).isEqualTo(
                "Are you someone who takes ownership, creates momentum and enjoys turning ideas into "
                        + "action? We are looking for a Partner Manager to join our Bid Excellence area. "
                        + "What matters to us is your ability to drive, collaborate and execute on our "
                        + "partner strategy.");
    }
}
