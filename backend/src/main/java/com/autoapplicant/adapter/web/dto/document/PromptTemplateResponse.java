package com.autoapplicant.adapter.web.dto.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** PromptTemplate plus the caller's per-user favourite flag. */
public record PromptTemplateResponse(
        UUID id,
        UUID userId,
        String name,
        String category,
        String description,
        String systemPrompt,
        String userPrompt,
        String outputConstraints,
        boolean isPublic,
        UUID parentTemplateId,
        int versionNumber,
        Instant createdAt,
        Instant updatedAt,
        boolean isSystem,
        List<String> tags,
        int usageCount,
        boolean favourite
) {
    public static PromptTemplateResponse from(PromptTemplate t, boolean favourite) {
        return new PromptTemplateResponse(t.id(), t.userId(), t.name(),
                t.category() != null ? t.category().name() : null,
                t.description(), t.systemPrompt(), t.userPrompt(), t.outputConstraints(),
                t.isPublic(), t.parentTemplateId(), t.versionNumber(),
                t.createdAt(), t.updatedAt(), t.isSystem(), t.tags(), t.usageCount(), favourite);
    }
}
