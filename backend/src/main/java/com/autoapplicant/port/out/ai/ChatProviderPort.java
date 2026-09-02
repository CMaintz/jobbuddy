package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;

/**
 * Text-generation capability. Implemented by every AI provider — including the local
 * CLI-agent, which can generate text but not embeddings. Generation use cases depend on
 * this narrow port, so the CLI agent is a valid substitute for them (ISP/LSP).
 *
 * <p>{@link #complete} is the one method a provider implements; everything else here is a
 * convenience over it. The {@code operation} label is what the usage log files the call
 * under, so a caller that names its calls gets a usage breakdown it can read.
 */
public interface ChatProviderPort {

    /** Fallback label for callers that do not name what they are generating. */
    String UNLABELLED_OPERATION = "GENERATION";

    /** Runs the prompt and reports the token cost the provider charged for it. */
    AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation);

    default String generate(PromptComposition composition) {
        return generate(composition, UNLABELLED_OPERATION);
    }

    default String generateJson(PromptComposition composition) {
        return generateJson(composition, UNLABELLED_OPERATION);
    }

    default String generate(PromptComposition composition, String operation) {
        return complete(composition, false, operation).text();
    }

    default String generateJson(PromptComposition composition, String operation) {
        return complete(composition, true, operation).text();
    }

    String chatModelName();
}
