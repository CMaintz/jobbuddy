package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.SkillCandidateDismissalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillCandidateDismissalJpaRepository
        extends JpaRepository<SkillCandidateDismissalEntity, UUID> {
    List<SkillCandidateDismissalEntity> findByUserId(UUID userId);
    boolean existsByUserIdAndNormalizedName(UUID userId, String normalizedName);
}
