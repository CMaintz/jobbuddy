package com.autoapplicant.domain.ai;

import java.util.Locale;

/**
 * Which provider and model the install embeds with. Vectors from different models cannot be
 * compared, so there is exactly one of these per install: the desktop app's profile vectors and
 * the scheduled crawl's posting vectors must come from the same model, or every match is noise.
 */
public record EmbeddingChoice(String provider, String model) {

    public static final String OPENAI = "openai";
    public static final String GEMINI = "gemini";
    /** Width of every stored vector: job_embeddings and profile_embeddings are vector(1536). */
    public static final int DIMENSIONS = 1536;

    public EmbeddingChoice {
        provider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!OPENAI.equals(provider) && !GEMINI.equals(provider)) {
            throw new IllegalArgumentException("Unknown embedding provider '" + provider
                    + "'; expected " + OPENAI + " or " + GEMINI);
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("An embedding choice needs a model");
        }
    }
}
