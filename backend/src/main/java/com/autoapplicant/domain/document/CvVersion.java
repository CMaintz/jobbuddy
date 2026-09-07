package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.UUID;

public record CvVersion(
        UUID id,
        UUID userId,
        String name,
        String content,
        String format,
        String fileUrl,
        boolean isPrimary,
        int versionNumber,
        Instant createdAt,
        Instant updatedAt
) {}
