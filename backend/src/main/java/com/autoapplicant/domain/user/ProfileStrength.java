package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

public record ProfileStrength(
        UUID id,
        UUID userId,
        String title,
        String description,
        String iconKey,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
