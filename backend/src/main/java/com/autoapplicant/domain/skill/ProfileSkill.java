package com.autoapplicant.domain.skill;

import java.util.UUID;

public record ProfileSkill(UUID id, UUID userId, String skillName, UUID taxonomyId,
                            String proficiencyLevel, Integer yearsExperience,
                            boolean usedInProduction, int displayOrder) {}
