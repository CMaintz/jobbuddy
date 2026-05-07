package com.autoapplicant.domain.ai;

import com.autoapplicant.domain.document.DocumentType;

import java.util.UUID;

public record AiGenerationRequest(
        UUID jobId,
        UUID cvVersionId,
        UUID promptTemplateId,
        UUID userId,
        String customInstructions,
        DocumentType documentType
) {}
