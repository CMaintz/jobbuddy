package com.autoapplicant.domain.document.structured;

import java.util.List;

public record CareerProfileForAi(
        String headline,
        String profile,
        List<String> skills,
        List<String> technologies,
        List<String> languages,
        List<StructuredDocumentItem> experience,
        List<StructuredDocumentItem> projects,
        List<StructuredDocumentItem> education,
        List<StructuredDocumentItem> certifications
) {}
