package com.autoapplicant.domain.ai;

public record AiAnalysisRequest(
        String cvContent,
        String jobDescription,
        String analysisType
) {}
