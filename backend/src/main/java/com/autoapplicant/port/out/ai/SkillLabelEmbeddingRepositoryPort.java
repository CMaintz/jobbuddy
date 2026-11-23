package com.autoapplicant.port.out.ai;

import java.util.Collection;
import java.util.Map;

/** Embeddings of skill labels, cached per label and embedding model. */
public interface SkillLabelEmbeddingRepositoryPort {

    /** The cached vectors among {@code labels} for {@code model}, keyed by label. */
    Map<String, float[]> find(String model, Collection<String> labels);

    /** Stores vectors for {@code model}; a label already cached keeps its vector. */
    void saveAll(String model, Map<String, float[]> vectors);
}
