package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.AiAnalysisRequest;
import com.autoapplicant.domain.ai.AiAnalysisResult;

import java.util.concurrent.CompletableFuture;

public interface AnalyzeCvUseCase {
    CompletableFuture<AiAnalysisResult> analyze(AiAnalysisRequest request);
}
