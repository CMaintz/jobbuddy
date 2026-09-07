package com.autoapplicant.adapter.web.dto.application;

public record AttachGeneratedDocumentRequest(
        String generatedContent,
        String status,
        String notes
) {}
