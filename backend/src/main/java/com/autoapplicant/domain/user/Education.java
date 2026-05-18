package com.autoapplicant.domain.user;

import com.autoapplicant.domain.skill.SkillTaxonomy;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
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
        Instant updatedAt,
        List<SkillTaxonomy> skills
) {}
