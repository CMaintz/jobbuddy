package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Fixture is a trimmed real page from jobdanmark.dk's search API. */
class JobdanmarkConnectorTest {

    private static final String DETAIL_HTML = "<html><body><h1>Afdelingsleder</h1></body></html>";

    private final JobdanmarkConnector connector = new JobdanmarkConnector();

    @Test
    void maps_a_search_hit_to_a_record_keyed_on_its_slug() {
        JsonNode item = CrawlerFixtures.json("jobdanmark/search-page.json").path("items").get(0);
        String slug = "afdelingsleder-til-den-sikrede-doegninstitution-egely-i-noerre-aaby"
                + "-med-primaert-ansvarsomraade-for-sikret-afdeling";

        RawJobData raw = connector.toRawJobData(item, DETAIL_HTML);

        assertThat(raw.source()).isEqualTo(JobSource.JOBDANMARK);
        assertThat(raw.sourceJobId()).isEqualTo(slug);
        assertThat(raw.url()).isEqualTo("https://jobdanmark.dk/job/" + slug);
        assertThat(raw.rawHtml()).isEqualTo(DETAIL_HTML);
        assertThat(raw.companyName()).isEqualTo("Egely, Sikret afdeling");
        assertThat(raw.location()).isEqualTo("Søndergaardsvej 9, 5580 Nørre Aaby");
        assertThat(raw.rawCategories()).containsExactly("fuldtid");
    }

    @Test
    void leaves_absent_company_and_address_null_rather_than_blank() {
        JsonNode item = CrawlerFixtures.parseJson("{\"url\": \"/job/lagermedarbejder\", \"jobTypes\": []}");

        RawJobData raw = connector.toRawJobData(item, DETAIL_HTML);

        assertThat(raw.sourceJobId()).isEqualTo("lagermedarbejder");
        assertThat(raw.companyName()).isNull();
        assertThat(raw.location()).isNull();
        assertThat(raw.rawCategories()).isEmpty();
    }
}
