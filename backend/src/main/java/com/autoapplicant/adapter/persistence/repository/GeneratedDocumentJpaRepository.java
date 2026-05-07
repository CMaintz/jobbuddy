package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.GeneratedDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GeneratedDocumentJpaRepository extends JpaRepository<GeneratedDocumentEntity, UUID> {
    List<GeneratedDocumentEntity> findByApplicationId(UUID applicationId);
    List<GeneratedDocumentEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
