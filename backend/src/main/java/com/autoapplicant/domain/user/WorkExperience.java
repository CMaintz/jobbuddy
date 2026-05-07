package com.autoapplicant.domain.user;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkExperience(
        UUID id,
        UUID userId,
        String companyName,
        String title,
        String location,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean isCurrent,
        List<String> technologies,
        List<String> achievements,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
