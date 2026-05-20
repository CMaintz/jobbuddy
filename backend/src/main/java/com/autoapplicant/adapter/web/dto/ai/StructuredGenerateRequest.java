package com.autoapplicant.adapter.web.dto.ai;

import com.autoapplicant.adapter.web.dto.DocumentThemeRequest;

import java.util.UUID;

public record StructuredGenerateRequest(
        UUID jobId,
        String jobDescription,
        String customInstructions,
        String targetLanguage,
        String templateId,
        Boolean showProfileImage,
        DocumentThemeRequest theme
) {}
