package com.autoapplicant.port.out.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;

import java.util.List;

public interface SkillTaxonomyRepositoryPort {
    List<SkillTaxonomy> searchByName(String query);
    List<SkillTaxonomy> findByCategory(String category);
    List<String> findAllCategories();
    SkillTaxonomy save(SkillTaxonomy skill);
    java.util.Optional<SkillTaxonomy> findByNormalizedName(String normalizedName);
    List<SkillTaxonomy> findByIds(java.util.Collection<java.util.UUID> ids);

    /** Children of the given nodes — the adjacency walk behind skill suggestions. */
    List<SkillTaxonomy> findByParentIds(java.util.Collection<java.util.UUID> parentIds);
}
