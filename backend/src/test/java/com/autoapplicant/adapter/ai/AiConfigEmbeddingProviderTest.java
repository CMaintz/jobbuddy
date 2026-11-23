package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

class AiConfigEmbeddingProviderTest {

    @Test
    void openai_embeds_with_the_stored_model_not_the_configured_default() {
        // The stored choice is what every process must agree on; a process whose config names
        // another OpenAI model would write vectors under that name instead.
        EmbeddingChoice stored = new EmbeddingChoice("openai", "text-embedding-3-large");

        String model = new AiConfig().embeddingProvider(mock(OpenAIClient.class), new AppProperties(), () -> stored)
                .embeddingModelName();

        assertThat(model).isEqualTo("text-embedding-3-large");
    }

    @Test
    void gemini_embeds_with_the_stored_model() {
        EmbeddingChoice stored = new EmbeddingChoice("gemini", "gemini-embedding-002");

        String model = new AiConfig().embeddingProvider(mock(OpenAIClient.class), new AppProperties(), () -> stored)
                .embeddingModelName();

        assertThat(model).isEqualTo("gemini-embedding-002");
    }
}
