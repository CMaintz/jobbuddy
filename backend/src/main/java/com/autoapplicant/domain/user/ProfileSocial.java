package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

public record ProfileSocial(
        UUID id,
        UUID userId,
        String platform,
        String url,
        String username,
        String iconKey,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
