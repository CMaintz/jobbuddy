package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillNames;
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

    @Override
    public SkillTaxonomy createOrGet(String name) {
        return createOrGet(name, null);
    }

    @Override
    public SkillTaxonomy createOrGet(String name, String category) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Skill name must not be blank");
        String trimmed = name.strip();
        // The same key every lookup path uses. Slugifying here is what made "C#" and "C++"
        // collide on "c-" and miss their seeded rows entirely.
        String normalized = SkillNames.normalize(trimmed);
        String resolvedCategory = (category != null && !category.isBlank()) ? category.trim() : "Custom";
        return repo.findByNormalizedName(normalized)
                .orElseGet(() -> repo.save(new SkillTaxonomy(null, trimmed, normalized, null, resolvedCategory, List.of())));
    }
}
