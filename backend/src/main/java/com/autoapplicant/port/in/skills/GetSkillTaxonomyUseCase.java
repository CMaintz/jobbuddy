package com.autoapplicant.port.in.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;

import java.util.List;

public interface GetSkillTaxonomyUseCase {
    List<SkillTaxonomy> search(String query);
    List<SkillTaxonomy> getByCategory(String category);
    List<String> getCategories();
    SkillTaxonomy createOrGet(String name);
    SkillTaxonomy createOrGet(String name, String category);
}
