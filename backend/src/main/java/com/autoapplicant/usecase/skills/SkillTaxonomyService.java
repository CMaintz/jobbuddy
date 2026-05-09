package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.in.skills.GetSkillTaxonomyUseCase;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkillTaxonomyService implements GetSkillTaxonomyUseCase {

    private final SkillTaxonomyRepositoryPort repo;

    public SkillTaxonomyService(SkillTaxonomyRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<SkillTaxonomy> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return repo.searchByName(query.trim());
    }

    @Override
    public List<SkillTaxonomy> getByCategory(String category) {
        return repo.findByCategory(category);
    }

    @Override
    public List<String> getCategories() {
        return repo.findAllCategories();
    }
}
