package com.autoapplicant.adapter.web.dto.ai;

import java.util.UUID;

public record GenerateDocumentRequest(
        UUID jobId,
        String jobDescription,
        String documentType,
        String templateId,
        String customInstructions,
        String targetLanguage,
        Boolean useStyleFromHistory
) {}
