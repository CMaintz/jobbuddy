package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.SkillGapReport;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AnalyzeSkillGapsUseCase {
    /**
     * Compares the PII-free master profile against the user's saved + applied
     * jobs and the nearest embedding-matched market jobs, and returns a
     * prioritized skill-gap heatmap with learning suggestions.
     */
    CompletableFuture<SkillGapReport> analyzeSkillGaps(UUID userId);
}
