package com.autoapplicant.domain.company;

import java.time.Instant;
import java.util.UUID;

public record Company(
        UUID id,
        String name,
        String slug,
        String website,
        String linkedinUrl,
        String description,
        String logoUrl,
        CompanySize sizeRange,
        String industry,
        String country,
        boolean isConsulting,
        boolean isRecruitingAgency,
        Instant createdAt,
        Instant updatedAt
) {}
