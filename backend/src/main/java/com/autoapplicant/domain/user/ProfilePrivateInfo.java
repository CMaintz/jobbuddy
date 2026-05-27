package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

public record ProfilePrivateInfo(
        UUID id,
        UUID userId,
        String fullName,
        String phone,
        String photoUrl,
        String location,
        String municipality,
        String contactEmail,
        Instant createdAt,
        Instant updatedAt
) {}
