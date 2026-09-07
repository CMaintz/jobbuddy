package com.autoapplicant.domain.document.structured;

import java.util.List;

public record CareerProfileForAi(
        String headline,
        String profile,
        List<String> skills,
        List<String> technologies,
        List<String> languages,
        List<String> spokenLanguages,
        List<StructuredDocumentItem> experience,
        List<StructuredDocumentItem> projects,
        List<StructuredDocumentItem> education,
        List<StructuredDocumentItem> certifications,
        List<String> strengths
) {}
