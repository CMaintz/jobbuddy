package com.autoapplicant.domain.document.structured;

import java.util.List;

public record AtsReport(
        int score,
        int keywordCoverage,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        List<AtsCheck> checks
) {}
