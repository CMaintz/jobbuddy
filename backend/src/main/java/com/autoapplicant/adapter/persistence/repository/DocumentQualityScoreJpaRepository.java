package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.DocumentQualityScoreEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentQualityScoreJpaRepository extends JpaRepository<DocumentQualityScoreEntity, UUID> {
    List<DocumentQualityScoreEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
