package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ResumeDraft(
        UUID id,
        UUID userId,
        String name,
        UUID jobId,
        UUID applicationId,
        Map<String, Object> resumeData,
        Map<String, Object> settings,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
