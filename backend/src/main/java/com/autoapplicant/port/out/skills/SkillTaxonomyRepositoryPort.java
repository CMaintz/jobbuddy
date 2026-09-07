package com.autoapplicant.port.out.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;

import java.util.List;

public interface SkillTaxonomyRepositoryPort {
    List<SkillTaxonomy> searchByName(String query);
    List<SkillTaxonomy> findByCategory(String category);
    List<String> findAllCategories();
    SkillTaxonomy save(SkillTaxonomy skill);
    java.util.Optional<SkillTaxonomy> findByNormalizedName(String normalizedName);

    /**
     * Bulk form of {@link #findByNormalizedName(String)} — one query for a whole profile's skills
     * rather than one per skill, since this runs on every document generation.
     */
    List<SkillTaxonomy> findByNormalizedNames(java.util.Collection<String> normalizedNames);
    List<SkillTaxonomy> findByIds(java.util.Collection<java.util.UUID> ids);

    /**
     * Every name the taxonomy already answers to — each row's normalized name plus its aliases,
     * normalized the same way. What the gap review subtracts from the market's labels.
     */
    java.util.Set<String> findAllKnownNormalizedNames();

    /** Children of the given nodes — the adjacency walk behind skill suggestions. */
    List<SkillTaxonomy> findByParentIds(java.util.Collection<java.util.UUID> parentIds);
}
