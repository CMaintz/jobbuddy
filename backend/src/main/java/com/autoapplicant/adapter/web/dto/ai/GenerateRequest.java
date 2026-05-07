package com.autoapplicant.adapter.web.dto.ai;

import com.autoapplicant.domain.document.DocumentType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GenerateRequest(
        UUID jobId,
        UUID cvVersionId,
        UUID promptTemplateId,
        @NotNull DocumentType documentType,
        String customInstructions,
        String targetLanguage
) {}
