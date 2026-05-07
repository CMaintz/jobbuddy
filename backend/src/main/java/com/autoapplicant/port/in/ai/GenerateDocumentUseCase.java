package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.AiGenerationRequest;
import com.autoapplicant.domain.ai.AiGenerationResult;

import java.util.concurrent.CompletableFuture;

public interface GenerateDocumentUseCase {
    CompletableFuture<AiGenerationResult> generate(AiGenerationRequest request);
}
