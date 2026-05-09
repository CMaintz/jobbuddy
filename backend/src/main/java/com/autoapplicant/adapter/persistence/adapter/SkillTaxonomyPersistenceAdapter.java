package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.SkillTaxonomyEntity;
import com.autoapplicant.adapter.persistence.repository.SkillTaxonomyJpaRepository;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

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
    public List<SkillTaxonomy> findByCategory(String category) {
        return repo.findByCategoryOrderByName(category).stream().map(this::toDomain).toList();
    }

    @Override
    public List<String> findAllCategories() {
        return repo.findAllCategories();
    }

    private SkillTaxonomy toDomain(SkillTaxonomyEntity e) {
        return new SkillTaxonomy(e.getId(), e.getName(), e.getNormalizedName(), e.getParentId(),
                e.getCategory(), e.getAliases() != null ? Arrays.asList(e.getAliases()) : List.of());
    }
}
