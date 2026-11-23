package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

/**
 * A Jobindex posting gets its company from the search page's Stash blob, joined on the tid in
 * the RSS item's link. The fixtures are a real RSS item and the matching Stash results, trimmed.
 *
 * <p>The test drives the join through {@code extractRssLink}, {@code extractTid} and
 * {@code extractStash} rather than {@code fetchJobs}, because the connector fetches with
 * {@code Jsoup.connect} and has no HTTP seam to feed it fixtures through.
 */
class JobindexStashJoinTest {

    private static final String FIXTURES = "/crawler/jobindex-stash-join/";

    private final JobindexConnector connector = new JobindexConnector();

    @Test
    void anRssItemResolvesToTheCompanyInItsStashEntry() throws Exception {
        Element item = Jsoup.parse(fixture("feed.rss"), "", Parser.xmlParser())
                .selectFirst("item");
        JsonNode stash = connector.extractStash(fixture("search.html"));

        String tid = JobindexConnector.extractTid(connector.extractRssLink(Objects.requireNonNull(item)));

        JsonNode entry = stash.findParents("tid").stream()
                .filter(result -> result.path("tid").asText().equals(tid))
                .findFirst().orElseThrow(() -> new AssertionError("no Stash entry for tid " + tid));
        assertThat(entry.path("company").path("name").asText()).isEqualTo("Whiteaway Group");
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = JobindexStashJoinTest.class.getResourceAsStream(FIXTURES + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
