package com.autoapplicant.domain.ai;

import java.util.List;

public record AiAnalysisResult(
        List<String> suggestions,
        int score,
        String rawResponse,
        String summary,
        List<String> strengths,
        List<String> gaps
) {
    /** Fallback shape for legacy/unparseable responses. */
    public static AiAnalysisResult unstructured(String text) {
        return new AiAnalysisResult(List.of(text), 0, text, null, List.of(), List.of());
    }
}
