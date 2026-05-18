package com.autoapplicant.domain.document.structured;

import com.autoapplicant.domain.document.DocumentType;

import java.util.List;

public record StructuredDocument(
        DocumentType documentType,
        String exportMode,
        String templateId,
        DocumentIdentity identity,
        List<StructuredDocumentSection> sections,
        String bodyContent,
        AtsReport atsReport
) {}
