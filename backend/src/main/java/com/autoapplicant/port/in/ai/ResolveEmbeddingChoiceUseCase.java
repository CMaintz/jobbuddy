package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.EmbeddingChoice;

/** The install's one embedding provider and model, settled once at startup. */
public interface ResolveEmbeddingChoiceUseCase {

    EmbeddingChoice currentChoice();
}
