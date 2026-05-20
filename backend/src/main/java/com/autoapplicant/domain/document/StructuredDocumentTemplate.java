package com.autoapplicant.domain.document;

import com.autoapplicant.domain.document.structured.DocumentTheme;

import java.util.List;

public record StructuredDocumentTemplate(
        String id,
        String familyId,
        String familyName,
        String label,
        String description,
        List<DocumentType> documentTypes,
        String layoutType,
        String exportMode,
        boolean supportsProfileImage,
        boolean atsSafe,
        int displayOrder,
        DocumentTheme defaultTheme
) {}
