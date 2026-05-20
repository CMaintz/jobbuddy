package com.autoapplicant.adapter.web.dto.ai;

import com.autoapplicant.adapter.web.dto.DocumentThemeRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record GenerateDocumentRequest(
        UUID jobId,
        String jobDescription,
        @NotBlank String documentType,
        String templateId,
        String customInstructions,
        String targetLanguage,
        Boolean showProfileImage,
        DocumentThemeRequest theme
) {}
