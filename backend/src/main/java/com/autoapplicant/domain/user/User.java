package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String googleId,
        String linkedinId,
        String firebaseUid,
        UserRole role,
        boolean emailVerified,
        boolean onboardingComplete,
        Instant createdAt,
        Instant updatedAt
) {}
