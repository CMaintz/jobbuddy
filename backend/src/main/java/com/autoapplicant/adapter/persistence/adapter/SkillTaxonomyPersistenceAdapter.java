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
    public List<SkillTaxonomy> findByParentIds(java.util.Collection<java.util.UUID> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return repo.findByParentIdIn(parentIds).stream().map(this::toDomain).toList();
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
    public List<SkillTaxonomy> findByIds(java.util.Collection<java.util.UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return repo.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    private SkillTaxonomy toDomain(SkillTaxonomyEntity e) {
        return new SkillTaxonomy(e.getId(), e.getName(), e.getNormalizedName(), e.getParentId(),
                e.getCategory(), e.getAliases() != null ? Arrays.asList(e.getAliases()) : List.of());
    }
}
