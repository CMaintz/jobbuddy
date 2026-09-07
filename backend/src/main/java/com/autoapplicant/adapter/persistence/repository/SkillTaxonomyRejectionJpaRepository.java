package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.SkillTaxonomyRejectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SkillTaxonomyRejectionJpaRepository
        extends JpaRepository<SkillTaxonomyRejectionEntity, String> {

    @Query("SELECT r.normalizedName FROM SkillTaxonomyRejectionEntity r")
    List<String> findAllNormalizedNames();
}
