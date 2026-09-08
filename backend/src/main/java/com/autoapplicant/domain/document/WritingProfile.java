package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WritingProfile(
        UUID id,
        UUID userId,
        String tone,
        String vocabularyNotes,
        List<String> phrasingPatterns,
        List<String> exampleExcerpts,
        /** Style rules to always follow (e.g. "lead with the outcome"). */
        List<String> dos,
        /** Style rules to never break (e.g. banned phrases, clichés to avoid). */
        List<String> donts,
        /** How documents should be structured (paragraph order, length, sign-off). */
        String structureNotes,
        Instant lastAnalyzedAt,
        Instant createdAt,
        Instant updatedAt
) {}
