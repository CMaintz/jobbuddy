package com.autoapplicant.domain.skill;

import java.util.List;
import java.util.UUID;

public record SkillTaxonomy(UUID id, String name, String normalizedName, UUID parentId,
                             String category, List<String> aliases) {}
