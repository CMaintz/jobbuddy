package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.SkillTaxonomyEntity;
import com.autoapplicant.adapter.persistence.repository.SkillTaxonomyJpaRepository;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class SkillTaxonomyPersistenceAdapter implements SkillTaxonomyRepositoryPort {

    private final SkillTaxonomyJpaRepository repo;

    public SkillTaxonomyPersistenceAdapter(SkillTaxonomyJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<SkillTaxonomy> searchByName(String query) {
        return repo.findByNameContainingIgnoreCaseOrNormalizedNameContainingIgnoreCase(query, query.toLowerCase())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public java.util.Set<String> findAllKnownNormalizedNames() {
        // The taxonomy is reference data in the hundreds of rows, and every one of them plus its
        // aliases is needed to answer "is this label already known" — one read beats a query per
        // candidate label.
        java.util.Set<String> known = new java.util.LinkedHashSet<>();
        for (SkillTaxonomyEntity entity : repo.findAll()) {
            known.add(com.autoapplicant.domain.skill.SkillNames.normalize(entity.getNormalizedName()));
            known.add(com.autoapplicant.domain.skill.SkillNames.normalize(entity.getName()));
            if (entity.getAliases() != null) {
                for (String alias : entity.getAliases()) {
                    if (alias != null && !alias.isBlank()) {
                        known.add(com.autoapplicant.domain.skill.SkillNames.normalize(alias));
                    }
                }
            }
        }
        known.remove("");
        return known;
    }

    @Override
    public List<SkillTaxonomy> findByParentIds(java.util.Collection<java.util.UUID> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return repo.findByParentIdIn(parentIds).stream().map(this::toDomain).toList();
    }

    @Override
    public List<SkillTaxonomy> findAll() {
        return repo.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<SkillTaxonomy> findByCategory(String category) {
        return repo.findByCategoryOrderByName(category).stream().map(this::toDomain).toList();
    }

    @Override
    public List<String> findAllCategories() {
        return repo.findAllCategories();
    }

    @Override
    public SkillTaxonomy save(SkillTaxonomy skill) {
        SkillTaxonomyEntity entity = new SkillTaxonomyEntity();
        entity.setName(skill.name());
        entity.setNormalizedName(skill.normalizedName());
        entity.setParentId(skill.parentId());
        entity.setCategory(skill.category());
        entity.setAliases(skill.aliases() != null ? skill.aliases().toArray(new String[0]) : null);
        return toDomain(repo.save(entity));
    }

    @Override
    public Optional<SkillTaxonomy> findByNormalizedName(String normalizedName) {
        return repo.findByNormalizedName(normalizedName).map(this::toDomain);
    }

    @Override
    public List<SkillTaxonomy> findByNormalizedNames(java.util.Collection<String> normalizedNames) {
        if (normalizedNames == null || normalizedNames.isEmpty()) return List.of();
        return repo.findByNormalizedNameIn(normalizedNames).stream().map(this::toDomain).toList();
    }

    @Override
    public List<SkillTaxonomy> findByIds(java.util.Collection<java.util.UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return repo.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    private SkillTaxonomy toDomain(SkillTaxonomyEntity e) {
        return new SkillTaxonomy(e.getId(), e.getName(), e.getNormalizedName(), e.getParentId(),
                e.getCategory(), e.getAliases() != null ? Arrays.asList(e.getAliases()) : List.of());
    }
}
