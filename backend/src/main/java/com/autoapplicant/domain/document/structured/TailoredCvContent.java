package com.autoapplicant.domain.document.structured;

import java.util.List;

public record TailoredCvContent(
        String selectedProfile,
        List<String> selectedSkills,
        List<StructuredDocumentItem> experience,
        List<StructuredDocumentItem> projects,
        List<StructuredDocumentItem> education,
        List<StructuredDocumentItem> certifications,
        List<String> notes
) {}
