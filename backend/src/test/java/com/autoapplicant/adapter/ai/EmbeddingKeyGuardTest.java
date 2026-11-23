package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.EmbeddingChoice;
import org.junit.jupiter.api.Test;

class EmbeddingKeyGuardTest {

    private static final EmbeddingChoice GEMINI = new EmbeddingChoice("gemini", "gemini-embedding-001");
    private static final EmbeddingChoice OPENAI = new EmbeddingChoice("openai", "text-embedding-3-small");

    private static AppProperties keys(String openAiKey, String geminiKey) {
        AppProperties props = new AppProperties();
        props.getOpenai().setApiKey(openAiKey);
        props.getGemini().setApiKey(geminiKey);
        return props;
    }

    @Test
    void a_missing_key_for_the_stored_provider_is_named_in_the_failure() {
        // The crawl's env may well carry an OpenAI key; what matters is the stored provider's.
        EmbeddingKeyGuard guard = new EmbeddingKeyGuard(() -> GEMINI, keys("sk-present", ""));

        assertThat(guard.missingKey()).contains(new EmbeddingKeyGuard.MissingKey("gemini", "GEMINI_API_KEY"));
        assertThatThrownBy(guard::requireKey)
                .isInstanceOf(MissingEmbeddingKeyException.class)
                .hasMessageContaining("gemini")
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void a_present_key_passes() {
        EmbeddingKeyGuard guard = new EmbeddingKeyGuard(() -> OPENAI, keys("sk-present", null));

        assertThat(guard.missingKey()).isEmpty();
        assertThatCode(guard::requireKey).doesNotThrowAnyException();
    }
}
