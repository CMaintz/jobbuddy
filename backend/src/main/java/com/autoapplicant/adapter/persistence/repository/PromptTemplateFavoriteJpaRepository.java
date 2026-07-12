package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.PromptTemplateFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromptTemplateFavoriteJpaRepository extends JpaRepository<PromptTemplateFavoriteEntity, UUID> {

    @Query("SELECT f.templateId FROM PromptTemplateFavoriteEntity f WHERE f.userId = :userId")
    List<UUID> findTemplateIdsByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndTemplateId(UUID userId, UUID templateId);

    void deleteByUserIdAndTemplateId(UUID userId, UUID templateId);
}
