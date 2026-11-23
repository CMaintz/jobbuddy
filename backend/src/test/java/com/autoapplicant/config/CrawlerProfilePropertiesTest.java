package com.autoapplicant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** The crawler profile's settings as they resolve from application.yml and its profile file. */
class CrawlerProfilePropertiesTest {

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {
    }

    private static String requireEnriched(String enrichmentEnabled) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .profiles("crawler")
                .run("--app.enrichment.sweep.enabled=" + enrichmentEnabled)) {
            return context.getEnvironment().getProperty("app.embedding.require-enriched");
        }
    }

    @Test
    void aCrawlWithEnrichmentOffEmbedsWithoutWaitingForIt() {
        // Bug: turning enrichment off without also setting EMBEDDING_REQUIRE_ENRICHED=false
        // leaves the embedding queue waiting for an enrichment that never comes; the run logs
        // "embedding only", embeds nothing and exits 0.
        assertThat(requireEnriched("false")).isEqualTo("false");
    }

    @Test
    void aCrawlThatEnrichesStillEmbedsTheTrimmedText() {
        // Bug: a crawl that does enrich embeds the raw text first and pays for every posting
        // twice once enrichment trims it.
        assertThat(requireEnriched("true")).isEqualTo("true");
    }
}
