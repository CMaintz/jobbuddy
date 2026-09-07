package com.autoapplicant.domain.ai;

import java.util.List;

public record AiAnalysisResult(
        List<String> suggestions,
        int score,
        String rawResponse
) {}
