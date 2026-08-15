package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.document.PromptComposition;

/**
 * Text-generation capability. Implemented by every AI provider — including the local
 * CLI-agent, which can generate text but not embeddings. Generation use cases depend on
 * this narrow port, so the CLI agent is a valid substitute for them (ISP/LSP).
 */
public interface ChatProviderPort {
    String generate(PromptComposition composition);
    String generateJson(PromptComposition composition);
    String chatModelName();
}
