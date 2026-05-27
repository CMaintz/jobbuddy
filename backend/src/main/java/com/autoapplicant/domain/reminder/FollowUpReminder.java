package com.autoapplicant.domain.reminder;

import java.time.Instant;
import java.util.UUID;

public record FollowUpReminder(
        UUID id,
        UUID applicationId,
        UUID userId,
        String note,
        Instant dueAt,
        boolean completed,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}
