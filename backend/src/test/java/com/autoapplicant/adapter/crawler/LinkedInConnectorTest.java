package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.crawler.LinkedInConnector.JobCard;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fixtures are hand-built from the jobs-guest markup the selectors target; LinkedIn is not
 * fetched for samples (its terms forbid automated access).
 */
class LinkedInConnectorTest {

    private final LinkedInConnector connector = new LinkedInConnector(null);

    @Test
    void parses_search_cards_and_drops_ones_without_a_numeric_job_id() {
        List<JobCard> cards = connector.parseCards(CrawlerFixtures.read("linkedin/search.html"));

        assertThat(cards).extracting(JobCard::id).containsExactly("4012345678", "4012345999");
        JobCard first = cards.get(0);
        assertThat(first.title()).isEqualTo("Backend Engineer");
        assertThat(first.company()).isEqualTo("Nordlys Software");
        assertThat(first.location()).isEqualTo("Copenhagen, Capital Region of Denmark, Denmark");
        assertThat(first.url()).isEqualTo(
                "https://dk.linkedin.com/jobs/view/backend-engineer-at-nordlys-software-4012345678");
        assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-10-28T00:00:00Z"));
    }

    @Test
    void builds_a_view_url_and_falls_back_to_the_screen_reader_title_for_a_bare_card() {
        JobCard bare = connector.parseCards(CrawlerFixtures.read("linkedin/search.html")).get(1);

        assertThat(bare.title()).isEqualTo("Data Analyst");
        assertThat(bare.url()).isEqualTo("https://www.linkedin.com/jobs/view/4012345999");
        assertThat(bare.postedAt()).isNull();
    }

    @Test
    void maps_a_detail_page_preferring_its_fields_over_the_card() {
        JobCard card = connector.parseCards(CrawlerFixtures.read("linkedin/search.html")).get(0);

        RawJobData raw = connector.toRawJobData(card, CrawlerFixtures.read("linkedin/detail.html"));

        assertThat(raw.source()).isEqualTo(JobSource.LINKEDIN);
        assertThat(raw.sourceJobId()).isEqualTo("4012345678");
        assertThat(raw.url()).isEqualTo(card.url());
        assertThat(raw.companyName()).isEqualTo("Nordlys Software ApS");
        assertThat(raw.location()).isEqualTo("Aarhus, Central Denmark Region, Denmark");
        assertThat(raw.postedAt()).isEqualTo(Instant.parse("2026-10-28T00:00:00Z"));
        assertThat(raw.shortDescription())
                .startsWith("Join our platform team to build resilient Java services.");
        assertThat(raw.rawHtml()).contains(
                "<h1>Senior Backend Engineer</h1>",
                "Seniority level: Mid-Senior level. Employment type: Full-time.");
    }

    @Test
    void skips_a_detail_page_with_neither_description_nor_criteria() {
        JobCard card = connector.parseCards(CrawlerFixtures.read("linkedin/search.html")).get(0);
        String expiredPosting = "<html><body><h2 class=\"top-card-layout__title\">Backend Engineer</h2>"
                + "<p>No longer accepting applications</p></body></html>";

        assertThat(connector.toRawJobData(card, expiredPosting)).isNull();
    }
}
