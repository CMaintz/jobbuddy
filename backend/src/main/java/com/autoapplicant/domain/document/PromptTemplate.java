package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A prompt persona.
 *
 * <p>{@code isProtected}, {@code isDefault} and {@code isSystem} are three different things that
 * used to be one:
 *
 * <ul>
 *   <li>{@code isSystem} — the app shipped it (kept for the existing admin surface).</li>
 *   <li>{@code isProtected} — the user may duplicate it but never edit or delete it. Customising
 *       a house prompt means editing your own copy, not rewriting app-owned content.</li>
 *   <li>{@code isDefault} — the seeded starting point for its category. Which template a given
 *       user actually gets is their own choice, stored separately; this is only the fallback.</li>
 * </ul>
 */
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
        Instant updatedAt,
        boolean isSystem,
        List<String> tags,
        int usageCount,
        boolean isProtected,
        boolean isDefault
) {}
