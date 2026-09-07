package com.autoapplicant.domain.user;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record Certification(
        UUID id,
        UUID userId,
        String name,
        String issuer,
        LocalDate issuedAt,
        LocalDate expiresAt,
        String credentialUrl,
        Instant createdAt
) {}
