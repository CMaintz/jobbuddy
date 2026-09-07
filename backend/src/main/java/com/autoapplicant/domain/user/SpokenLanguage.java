package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

public record SpokenLanguage(
        UUID id,
        UUID userId,
        String language,
        LanguageProficiency proficiency,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
