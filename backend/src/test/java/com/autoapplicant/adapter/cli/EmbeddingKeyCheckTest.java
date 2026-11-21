package com.autoapplicant.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.adapter.ai.EmbeddingKeyGuard;
import com.autoapplicant.adapter.ai.MissingEmbeddingKeyException;
import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.EmbeddingChoice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

class EmbeddingKeyCheckTest {

    private static EmbeddingKeyGuard guard(String geminiKey) {
        AppProperties props = new AppProperties();
        props.getGemini().setApiKey(geminiKey);
        props.getOpenai().setApiKey("sk-in-the-workflow-env");
        return new EmbeddingKeyGuard(() -> new EmbeddingChoice("gemini", "gemini-embedding-001"), props);
    }

    @Test
    void the_crawl_fails_before_crawling_when_the_stored_provider_has_no_key() {
        // Without it every posting's embedding fails one by one, burns its attempts, and the run
        // exits 0 with nothing searchable.
        assertThatThrownBy(() -> new EmbeddingKeyCheck(guard("")).run(new DefaultApplicationArguments()))
                .isInstanceOf(MissingEmbeddingKeyException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void with_the_key_the_crawl_goes_ahead() {
        assertThatCode(() -> new EmbeddingKeyCheck(guard("gemini-key")).run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void the_enrich_command_is_checked_too_since_it_drains_embeddings() {
        assertThat(EmbeddingKeyCheck.class.getAnnotation(Profile.class).value()).contains("crawler", "enrich");
        assertThat(EmbeddingKeyCheck.class.getAnnotation(Order.class).value())
                .isLessThan(EnrichmentSweepRunner.class.getAnnotation(Order.class).value());
    }

    @Test
    void it_runs_before_the_crawl() {
        // Spring runs ApplicationRunners in @Order; after the crawl, the check would come too late.
        assertThat(EmbeddingKeyCheck.class.getAnnotation(Order.class).value())
                .isLessThan(CrawlerCliRunner.class.getAnnotation(Order.class).value());
    }
}
