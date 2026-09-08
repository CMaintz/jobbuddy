package com.autoapplicant.port.out.ai;

/**
 * Embedding capability — turning text into a vector for semantic search. Only real API
 * providers (OpenAI/Gemini) implement this; the CLI-agent deliberately does not.
 */
public interface EmbeddingProviderPort {
    float[] embed(String text);
    String embeddingModelName();
}
