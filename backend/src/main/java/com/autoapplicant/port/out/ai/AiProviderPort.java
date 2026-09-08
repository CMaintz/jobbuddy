package com.autoapplicant.port.out.ai;

/**
 * A provider that can do both text generation and embeddings (OpenAI/Gemini). Used where a
 * single component needs both — notably the enrichment path (JSON generation + embeddings).
 * Generation-only use cases should depend on {@link ChatProviderPort} instead, so the
 * CLI-agent (chat-only) can substitute for them.
 */
public interface AiProviderPort extends ChatProviderPort, EmbeddingProviderPort {
}
