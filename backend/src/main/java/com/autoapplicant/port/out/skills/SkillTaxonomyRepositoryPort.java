package com.autoapplicant.port.out.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;

import java.util.List;

public interface SkillTaxonomyRepositoryPort {
    List<SkillTaxonomy> searchByName(String query);
    List<SkillTaxonomy> findByCategory(String category);
    List<String> findAllCategories();
    SkillTaxonomy save(SkillTaxonomy skill);
    java.util.Optional<SkillTaxonomy> findByNormalizedName(String normalizedName);
}
