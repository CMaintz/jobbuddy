package com.autoapplicant.domain.document.structured;

import java.util.List;

public record TailoredCvContent(
        String selectedProfile,
        List<String> selectedSkills,
        List<StructuredDocumentItem> experience,
        List<StructuredDocumentItem> projects,
        List<StructuredDocumentItem> education,
        List<StructuredDocumentItem> certifications,
        Integer keywordCoverage,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        List<String> notes
) {}
