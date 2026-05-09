package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.SkillTaxonomyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SkillTaxonomyJpaRepository extends JpaRepository<SkillTaxonomyEntity, UUID> {
    List<SkillTaxonomyEntity> findByNameContainingIgnoreCaseOrNormalizedNameContainingIgnoreCase(
            String name, String normalizedName);
    List<SkillTaxonomyEntity> findByCategoryOrderByName(String category);

    @Query("SELECT DISTINCT e.category FROM SkillTaxonomyEntity e WHERE e.category IS NOT NULL ORDER BY e.category")
    List<String> findAllCategories();
}
