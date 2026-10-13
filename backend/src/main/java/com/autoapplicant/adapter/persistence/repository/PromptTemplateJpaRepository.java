package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.PromptTemplateEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromptTemplateJpaRepository extends JpaRepository<PromptTemplateEntity, UUID> {
    /**
     * The user's library: their own templates and the shared ones they favourited or chose as a
     * default, followed by the app's. A shared template drops out the moment its author makes it
     * private again.
     */
    @Query("SELECT t FROM PromptTemplateEntity t WHERE t.userId = :userId OR t.isSystem = TRUE"
            + " OR (t.isPublic = TRUE AND (t.id IN (SELECT f.templateId FROM PromptTemplateFavoriteEntity f"
            + " WHERE f.userId = :userId) OR t.id IN (SELECT d.templateId FROM UserDefaultPromptEntity d"
            + " WHERE d.userId = :userId))) ORDER BY t.isSystem ASC, t.createdAt DESC")
    List<PromptTemplateEntity> findByUserIdOrSystem(@Param("userId") UUID userId);

    long countByUserIdAndCategory(UUID userId, String category);

    List<PromptTemplateEntity> findByIsPublicTrueAndIsProtectedFalseOrderByCreatedAtDesc();

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
