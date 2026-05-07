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
        Instant lastAnalyzedAt,
        Instant createdAt,
        Instant updatedAt
) {}
