package com.autoapplicant.domain.user;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Project(
        UUID id,
        UUID userId,
        String name,
        String description,
        List<String> technologies,
        String githubUrl,
        String liveUrl,
        String architectureNotes,
        String measurableOutcomes,
        String businessImpact,
        LocalDate startDate,
        LocalDate endDate,
        boolean isFeatured,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
