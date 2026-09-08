package com.autoapplicant.adapter.web.dto.document;

import com.autoapplicant.domain.document.PromptCategory;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreatePromptTemplateRequest(
        @NotBlank String name,
        PromptCategory category,
        String description,
        String systemPrompt,
        @NotBlank String userPrompt,
        String outputConstraints,
        boolean isPublic,
        List<String> tags
) {}
