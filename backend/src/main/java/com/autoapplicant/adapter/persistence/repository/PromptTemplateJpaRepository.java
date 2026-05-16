package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromptTemplateJpaRepository extends JpaRepository<PromptTemplateEntity, UUID> {
    /** Returns the user's own templates, followed by system templates. */
    @Query("SELECT t FROM PromptTemplateEntity t WHERE t.userId = :userId OR t.isSystem = TRUE ORDER BY t.isSystem ASC, t.createdAt DESC")
    List<PromptTemplateEntity> findByUserIdOrSystem(@Param("userId") UUID userId);

    List<PromptTemplateEntity> findByIsPublicTrueOrderByCreatedAtDesc();
}
