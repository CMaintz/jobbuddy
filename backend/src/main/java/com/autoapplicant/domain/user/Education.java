package com.autoapplicant.domain.user;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record Education(
        UUID id,
        UUID userId,
        String institution,
        String degree,
        String fieldOfStudy,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        String grade,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
