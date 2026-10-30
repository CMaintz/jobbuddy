package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/** Fixture is Bankdata's real Teamtailor feed, trimmed to two items with short descriptions. */
class TeamtailorConnectorTest {

    private final TeamtailorConnector connector = new TeamtailorConnector(null);

    @Test
    void maps_an_rss_item_with_teamtailor_namespace_fields() {
        Element item = CrawlerFixtures.rssItems("teamtailor/jobs.rss").get(1);
        String link = connector.extractRssLink(item);

        RawJobData raw = connector.toRawJobData(item, item.select("guid").text().trim(), link);

        assertThat(link).isEqualTo("https://bankdata.teamtailor.com/jobs/8608036-backend-developer");
        assertThat(raw.source()).isEqualTo(JobSource.TEAMTAILOR);
        assertThat(raw.sourceJobId()).isEqualTo("1eaf8535-88b2-427b-8d67-12e096f4c59a");
        assertThat(raw.rawCategories()).containsExactly("Infrastructure, Operations & Security");
        assertThat(raw.rawHtml())
                .contains(
                        "Title: Backend Developer\n",
                        "URL: https://bankdata.teamtailor.com/jobs/8608036-backend-developer\n",
                        "Department: Infrastructure, Operations & Security\n",
                        "Role: Software Development\n",
                        "Description: I Document Services udvikler vi en ny dokumentplatform")
                .containsPattern("Location: .*Fredericia");
    }
}
