package com.autoapplicant.adapter.web.dto.pdf;

import com.autoapplicant.domain.document.structured.StructuredDocument;

public record StructuredDocumentExportRequest(
        StructuredDocument document
) {}
