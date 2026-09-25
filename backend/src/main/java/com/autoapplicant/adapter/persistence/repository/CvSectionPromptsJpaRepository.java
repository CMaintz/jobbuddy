package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CvSectionPromptsEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvSectionPromptsJpaRepository extends JpaRepository<CvSectionPromptsEntity, UUID> {
    Optional<CvSectionPromptsEntity> findByUserId(UUID userId);
}
