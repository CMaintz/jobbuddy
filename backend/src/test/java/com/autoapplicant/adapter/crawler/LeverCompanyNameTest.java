package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** The fixture is a real posting from the Lever postings API, trimmed. */
class LeverCompanyNameTest {

    @Test
    void aPostingCarriesTheCompanyItWasCrawledFor() {
        AppProperties props = new AppProperties();
        props.getLever().setCompanies(List.of("veo"));
        List<RawJobData> found = new ArrayList<>();

        new FixtureLeverConnector(props).fetchJobs(new CrawlConfig(
                JobSource.LEVER, 1, 0L, List.of(), guid -> false, found::add, guid -> { }, false));

        assertThat(found).singleElement()
                .extracting(RawJobData::companyName)
                .isEqualTo("veo");
    }

    @Test
    void aBoardWithAConfiguredDisplayNameCarriesThatNameButKeepsItsSlugInTheId() {
        // Bug: the slug "veo" is stored as the company and never lines up with the
        // "Veo Technologies" other sources use; putting the display name in the external id
        // instead would re-ingest every Lever posting as new.
        AppProperties props = new AppProperties();
        props.getLever().setCompanies(List.of("veo"));
        props.getLever().setDisplayNames(Map.of("veo", "Veo Technologies"));
        List<RawJobData> found = new ArrayList<>();

        new FixtureLeverConnector(props).fetchJobs(new CrawlConfig(
                JobSource.LEVER, 1, 0L, List.of(), guid -> false, found::add, guid -> { }, false));

        assertThat(found).singleElement().satisfies(posting -> {
            assertThat(posting.companyName()).isEqualTo("Veo Technologies");
            assertThat(posting.sourceJobId()).startsWith("lever-veo-");
        });
    }

    /** Serves the fixture instead of calling the Lever API. */
    private static final class FixtureLeverConnector extends LeverConnector {

        FixtureLeverConnector(AppProperties props) {
            super(props);
        }

        @Override
        protected String fetchWithRetry(String url, int maxRetries) {
            try (InputStream in = getClass().getResourceAsStream(
                    "/crawler/lever-company/postings.json")) {
                return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
