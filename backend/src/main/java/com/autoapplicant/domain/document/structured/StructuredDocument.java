package com.autoapplicant.domain.document.structured;

import com.autoapplicant.domain.document.DocumentType;

import java.util.List;
import java.util.UUID;

public record StructuredDocument(
        UUID generatedDocumentId,
        DocumentType documentType,
        String exportMode,
        String templateId,
        DocumentIdentity identity,
        DocumentRenderOptions options,
        List<StructuredDocumentSection> sections,
        String bodyContent,
        AtsReport atsReport
) {}
