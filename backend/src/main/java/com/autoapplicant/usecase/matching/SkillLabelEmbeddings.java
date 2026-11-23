package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.port.out.ai.SkillLabelEmbeddingRepositoryPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Skill similarity from embeddings of the labels, each label embedded once per model.
 *
 * <p>Labels are shared across users and postings ("Kommunikation" is asked for everywhere), so
 * the cache is global and a feed only pays for labels nobody has scored before, in one batched
 * call. Vectors from different models cannot be compared, so the cache is keyed by the model the
 * install embeds with (#319), and a model switch simply starts a new one.
 *
 * <p>Matching must not depend on this: when the provider fails, the feed is scored on spelling
 * alone rather than not shown.
 */
@Service
public class SkillLabelEmbeddings implements SkillSimilaritySource {

    private static final Logger log = LoggerFactory.getLogger(SkillLabelEmbeddings.class);
    /** The label column's width; anything longer is prose, not a skill. */
    private static final int MAX_LABEL = 200;
    /** Inputs per embedding request, well inside what the providers accept. */
    private static final int BATCH = 500;

    private final EmbeddingProviderPort provider;
    private final SkillLabelEmbeddingRepositoryPort cache;

    public SkillLabelEmbeddings(@Qualifier("embeddingProvider") EmbeddingProviderPort provider,
                                SkillLabelEmbeddingRepositoryPort cache) {
        this.provider = provider;
        this.cache = cache;
    }

    @Override
    public SkillSimilarity among(Collection<String> labels) {
        try {
            Map<String, float[]> vectors = vectorsFor(keys(labels));
            return (a, b) -> cosine(vectors.get(SkillNames.normalize(a)),
                    vectors.get(SkillNames.normalize(b)));
        } catch (RuntimeException e) {
            log.warn("Skill similarity unavailable, matching on spelling only: {}", e.getMessage());
            return SkillSimilarity.NONE;
        }
    }

    private Map<String, float[]> vectorsFor(TreeSet<String> keys) {
        String model = provider.embeddingModelName();
        Map<String, float[]> vectors = new HashMap<>(cache.find(model, keys));
        keys.removeAll(vectors.keySet());
        if (!keys.isEmpty()) vectors.putAll(embedAndCache(model, List.copyOf(keys)));
        return vectors;
    }

    /** A failed cache write costs a re-embed on the next feed, not this feed's similarities. */
    private Map<String, float[]> embedAndCache(String model, List<String> missing) {
        Map<String, float[]> fresh = embed(missing);
        try {
            cache.saveAll(model, fresh);
        } catch (RuntimeException e) {
            log.warn("Could not cache {} skill label embeddings: {}", fresh.size(), e.getMessage());
        }
        log.info("Embedded {} new skill labels with {}", fresh.size(), model);
        return fresh;
    }

    private Map<String, float[]> embed(List<String> missing) {
        Map<String, float[]> fresh = new HashMap<>();
        for (int from = 0; from < missing.size(); from += BATCH) {
            List<String> batch = missing.subList(from, Math.min(from + BATCH, missing.size()));
            List<float[]> vectors = provider.embedAll(batch);
            for (int i = 0; i < batch.size(); i++) fresh.put(batch.get(i), vectors.get(i));
        }
        return fresh;
    }

    private static TreeSet<String> keys(Collection<String> labels) {
        TreeSet<String> keys = new TreeSet<>();
        labels.stream().map(SkillNames::normalize)
                .filter(key -> !key.isEmpty() && key.length() <= MAX_LABEL).forEach(keys::add);
        return keys;
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
    }
}
