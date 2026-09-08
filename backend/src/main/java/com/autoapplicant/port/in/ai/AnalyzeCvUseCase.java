package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.AiAnalysisResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AnalyzeCvUseCase {
    /**
     * Analyzes the user's CV against an optional target job.
     * When cvVersionId is null, the master career profile is analyzed
     * (PII-free — identity fields never reach the AI provider).
     * The job context comes from jobId when given, else rawJobDescription.
     */
    CompletableFuture<AiAnalysisResult> analyze(UUID userId, UUID cvVersionId,
                                                UUID jobId, String rawJobDescription);
}
