package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.PdfTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PdfTemplateJpaRepository extends JpaRepository<PdfTemplateEntity, UUID> {
    @Query("SELECT e FROM PdfTemplateEntity e WHERE e.isActive = true AND (e.isSystem = true OR e.userId = :userId) ORDER BY e.isSystem DESC, e.name ASC")
    List<PdfTemplateEntity> findAvailableForUser(UUID userId);
}
