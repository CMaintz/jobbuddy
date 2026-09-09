package com.autoapplicant.port.out.ai;

/**
 * Embedding capability — turning text into a vector for semantic search. Only real API
 * providers (OpenAI/Gemini) implement this; the CLI-agent deliberately does not.
 */
public interface EmbeddingProviderPort {
    float[] embed(String text);

    /**
     * Embeds several texts in one call, returning vectors in the same order.
     *
     * <p>The embeddings endpoint takes an array, so a 200-posting batch is one round trip rather
     * than 200. That is latency and rate-limit headroom, not a token saving — the billing is per
     * token either way. Defaults to looping so a provider without batch support still works.
     */
    default java.util.List<float[]> embedAll(java.util.List<String> texts) {
        if (texts == null || texts.isEmpty()) return java.util.List.of();
        return texts.stream().map(this::embed).toList();
    }
    String embeddingModelName();
}
