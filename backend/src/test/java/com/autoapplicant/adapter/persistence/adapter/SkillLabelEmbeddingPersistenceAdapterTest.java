package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(SkillLabelEmbeddingPersistenceAdapter.class)
class SkillLabelEmbeddingPersistenceAdapterTest extends PostgresIntegrationTest {

    private static final String SMALL = "text-embedding-3-small";

    @Autowired SkillLabelEmbeddingPersistenceAdapter cache;

    @Test
    void a_vector_reads_back_as_stored_and_only_for_its_model() {
        cache.saveAll(SMALL, Map.of("risikostyring", new float[] {0.125f, -0.5f, 0.75f}));

        assertThat(cache.find(SMALL, List.of("risikostyring", "beredskab")))
                .containsOnlyKeys("risikostyring")
                .hasEntrySatisfying("risikostyring", vector ->
                        assertThat(vector).containsExactly(0.125f, -0.5f, 0.75f));
        assertThat(cache.find("gemini-embedding-001", List.of("risikostyring"))).isEmpty();
    }

    @Test
    void a_label_cached_by_a_concurrent_feed_keeps_its_first_vector() {
        cache.saveAll(SMALL, Map.of("beredskab", new float[] {1f}));

        cache.saveAll(SMALL, Map.of("beredskab", new float[] {2f}));

        assertThat(cache.find(SMALL, List.of("beredskab")).get("beredskab")).containsExactly(1f);
    }
}
