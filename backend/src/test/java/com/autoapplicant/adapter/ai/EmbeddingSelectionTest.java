package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingSelectionTest {

    private static final EmbeddingChoice GEMINI = new EmbeddingChoice("gemini", "gemini-embedding-001");

    @Test
    void embeds_with_the_provider_of_the_stored_choice() {
        List<EmbeddingChoice> built = new ArrayList<>();
        SelectedEmbeddingProvider provider = new SelectedEmbeddingProvider(() -> GEMINI, choice -> {
            built.add(choice);
            return fixed(choice.model());
        });

        assertThat(provider.embeddingModelName()).isEqualTo("gemini-embedding-001");
        provider.embed("tekst");
        assertThat(built).containsExactly(GEMINI);
    }

    private static EmbeddingProviderPort fixed(String model) {
        return new EmbeddingProviderPort() {
            @Override
            public float[] embed(String text) {
                return new float[] {1f};
            }

            @Override
            public String embeddingModelName() {
                return model;
            }
        };
    }
}
