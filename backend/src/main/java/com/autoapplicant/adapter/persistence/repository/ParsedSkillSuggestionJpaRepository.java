package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ParsedSkillSuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParsedSkillSuggestionJpaRepository extends JpaRepository<ParsedSkillSuggestionEntity, UUID> {
    List<ParsedSkillSuggestionEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);
    boolean existsByUserIdAndNormalizedName(UUID userId, String normalizedName);
    void deleteByUserIdAndNormalizedName(UUID userId, String normalizedName);
}
