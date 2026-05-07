package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromptTemplateJpaRepository extends JpaRepository<PromptTemplateEntity, UUID> {
    List<PromptTemplateEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<PromptTemplateEntity> findByIsPublicTrueOrderByCreatedAtDesc();
}
