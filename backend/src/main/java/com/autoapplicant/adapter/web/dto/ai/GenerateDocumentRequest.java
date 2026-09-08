package com.autoapplicant.adapter.web.dto.ai;

import com.autoapplicant.adapter.web.dto.DocumentThemeRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record GenerateDocumentRequest(
        UUID jobId,
        String jobDescription,
        @NotBlank String documentType,
        String templateId,
        UUID promptTemplateId,
        String customInstructions,
        String motivationText,
        String targetLanguage,
        Boolean showProfileImage,
        DocumentThemeRequest theme,
        String lengthPreference
) {}
