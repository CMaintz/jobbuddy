package com.autoapplicant.adapter.web.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
        @NotBlank String currentContent,
        String documentType,
        String jobDescription,
        String targetLanguage
) {}
