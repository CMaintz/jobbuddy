package com.autoapplicant.adapter.web.dto.pdf;

import com.autoapplicant.domain.document.DocumentType;

public record StructuredApplicationRequest(
        DocumentType documentType,
        String content,
        String templateId
) {}
