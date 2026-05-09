package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.GeneratedDocumentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GeneratedDocumentJpaRepository extends JpaRepository<GeneratedDocumentEntity, UUID> {
    List<GeneratedDocumentEntity> findByApplicationId(UUID applicationId);
    List<GeneratedDocumentEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<GeneratedDocumentEntity> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    @Query("SELECT e FROM GeneratedDocumentEntity e WHERE e.userId = :userId AND e.documentType = :documentType ORDER BY e.createdAt DESC")
    List<GeneratedDocumentEntity> findRecentByUserIdAndType(UUID userId, String documentType, Pageable pageable);
}
