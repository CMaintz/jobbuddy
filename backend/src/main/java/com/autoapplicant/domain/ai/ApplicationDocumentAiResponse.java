package com.autoapplicant.domain.ai;

import java.util.List;

public record ApplicationDocumentAiResponse(
        String body,
        Integer keywordCoverage,
        List<String> matchedKeywords,
        List<String> missingKeywords
) {}
