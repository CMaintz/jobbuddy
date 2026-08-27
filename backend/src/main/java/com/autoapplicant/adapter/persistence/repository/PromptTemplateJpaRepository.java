package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateJpaRepository extends JpaRepository<PromptTemplateEntity, UUID> {
    /** Returns the user's own templates, followed by system templates. */
    @Query("SELECT t FROM PromptTemplateEntity t WHERE t.userId = :userId OR t.isSystem = TRUE ORDER BY t.isSystem ASC, t.createdAt DESC")
    List<PromptTemplateEntity> findByUserIdOrSystem(@Param("userId") UUID userId);

    List<PromptTemplateEntity> findByIsPublicTrueOrderByCreatedAtDesc();

    /**
     * The default persona for a category. Ordered explicitly: without it the "default" was
     * whichever row the database happened to return, which made the app's actual voice a matter of
     * insertion order. The is_default flag decides; created_at only breaks a tie.
     */
    Optional<PromptTemplateEntity> findFirstByCategoryAndIsSystemTrueOrderByIsDefaultDescCreatedAtAsc(
            String category);

    @Modifying
    @Query("UPDATE PromptTemplateEntity t SET t.usageCount = t.usageCount + 1 WHERE t.id = :id")
    void incrementUsage(@Param("id") UUID id);
}
