package com.autoapplicant.adapter.web.dto.ai;

import java.util.UUID;

public record StructuredGenerateRequest(
        UUID jobId,
        String jobDescription,
        String customInstructions,
        String targetLanguage,
        String templateId
) {}
