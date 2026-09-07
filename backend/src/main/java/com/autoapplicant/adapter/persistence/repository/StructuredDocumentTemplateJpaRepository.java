package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.StructuredDocumentTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StructuredDocumentTemplateJpaRepository extends JpaRepository<StructuredDocumentTemplateEntity, UUID> {
    List<StructuredDocumentTemplateEntity> findByActiveTrueOrderByDisplayOrderAscNameAsc();
}
