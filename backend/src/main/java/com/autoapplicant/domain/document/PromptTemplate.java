package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.UUID;

public record PromptTemplate(
        UUID id,
        UUID userId,
        String name,
        PromptCategory category,
        String description,
        String systemPrompt,
        String userPrompt,
        String outputConstraints,
        boolean isPublic,
        UUID parentTemplateId,
        int versionNumber,
        Instant createdAt,
        Instant updatedAt
) {}
