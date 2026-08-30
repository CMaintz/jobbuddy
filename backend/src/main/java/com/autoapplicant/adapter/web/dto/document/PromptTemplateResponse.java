package com.autoapplicant.adapter.web.dto.document;

import com.autoapplicant.domain.document.PromptTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PromptTemplate plus the caller's per-user flags.
 *
 * <p>{@code isProtected} and {@code isSelectedDefault} are what the UI needs to render the row
 * correctly: a protected prompt offers Duplicate where a user's own offers Edit and Delete, and
 * exactly one row per category is the one currently in use.
 */
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
        boolean favourite,
        /** App-origin: duplicable, never editable or deletable. */
        boolean isProtected,
        /** The app's seeded starting point for this category. */
        boolean isDefault,
        /** The template this caller actually gets for the category right now. */
        boolean isSelectedDefault
) {
    public static PromptTemplateResponse from(PromptTemplate t, boolean favourite) {
        return from(t, favourite, false);
    }

    public static PromptTemplateResponse from(PromptTemplate t, boolean favourite,
                                              boolean isSelectedDefault) {
        return new PromptTemplateResponse(t.id(), t.userId(), t.name(),
                t.category() != null ? t.category().name() : null,
                t.description(), t.systemPrompt(), t.userPrompt(), t.outputConstraints(),
                t.isPublic(), t.parentTemplateId(), t.versionNumber(),
                t.createdAt(), t.updatedAt(), t.isSystem(), t.tags(), t.usageCount(), favourite,
                t.isProtected(), t.isDefault(), isSelectedDefault);
    }
}
