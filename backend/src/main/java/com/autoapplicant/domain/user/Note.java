package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

public record Note(
        UUID id,
        UUID userId,
        UUID jobId,
        UUID applicationId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {}
