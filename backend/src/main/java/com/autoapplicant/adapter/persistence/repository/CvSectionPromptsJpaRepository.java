package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CvSectionPromptsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CvSectionPromptsJpaRepository extends JpaRepository<CvSectionPromptsEntity, UUID> {
    Optional<CvSectionPromptsEntity> findByUserId(UUID userId);
}
