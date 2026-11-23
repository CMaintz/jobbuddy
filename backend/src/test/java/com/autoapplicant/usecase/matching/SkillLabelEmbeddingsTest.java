package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.port.out.ai.SkillLabelEmbeddingRepositoryPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillLabelEmbeddingsTest {

    private final FakeProvider provider = new FakeProvider();
    private final InMemoryCache cache = new InMemoryCache();
    private final SkillLabelEmbeddings embeddings = new SkillLabelEmbeddings(provider, cache);

    @Test
    void a_label_is_embedded_once_and_read_from_the_cache_after() {
        embeddings.among(List.of("Risikostyring", "Risikovurdering"));
        embeddings.among(List.of("Risikostyring", "Risikovurdering", "Beredskab"));

        assertThat(provider.embedded).containsExactly(
                List.of("risikostyring", "risikovurdering"), List.of("beredskab"));
    }

    @Test
    void a_new_embedding_model_embeds_its_own_vectors() {
        embeddings.among(List.of("Risikostyring"));
        provider.model = "gemini-embedding-001";

        embeddings.among(List.of("Risikostyring"));

        assertThat(provider.embedded).hasSize(2);
    }

    @Test
    void labels_compare_whatever_their_case_and_spacing() {
        SkillSimilarity similarity = embeddings.among(List.of("Risikostyring", "risikovurdering"));

        assertThat(similarity.between(" RISIKOSTYRING", "Risikovurdering")).isGreaterThan(0.9);
    }

    @Test
    void a_failing_provider_leaves_matching_on_spelling_alone() {
        provider.fail = true;

        SkillSimilarity similarity = embeddings.among(List.of("Risikostyring", "Risikovurdering"));

        assertThat(similarity.between("Risikostyring", "Risikovurdering")).isZero();
    }

    /** Vectors that make "risiko*" labels near-identical and everything else orthogonal. */
    private static final class FakeProvider implements EmbeddingProviderPort {
        final List<List<String>> embedded = new ArrayList<>();
        String model = "text-embedding-3-small";
        boolean fail;

        @Override
        public float[] embed(String text) {
            return text.startsWith("risiko") ? new float[] {1f, 0.1f} : new float[] {0f, 1f};
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            if (fail) throw new IllegalStateException("401 from the embedding API");
            embedded.add(List.copyOf(texts));
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public String embeddingModelName() {
            return model;
        }
    }

    private static final class InMemoryCache implements SkillLabelEmbeddingRepositoryPort {
        private final Map<String, float[]> rows = new HashMap<>();

        @Override
        public Map<String, float[]> find(String model, Collection<String> labels) {
            Map<String, float[]> found = new HashMap<>();
            labels.forEach(label -> {
                float[] vector = rows.get(model + "|" + label);
                if (vector != null) found.put(label, vector);
            });
            return found;
        }

        @Override
        public void saveAll(String model, Map<String, float[]> vectors) {
            vectors.forEach((label, vector) -> rows.putIfAbsent(model + "|" + label, vector));
        }
    }
}
