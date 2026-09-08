package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.LinkedInQueryPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LinkedInQueryPlanJpaRepository extends JpaRepository<LinkedInQueryPlanEntity, UUID> {
    Optional<LinkedInQueryPlanEntity> findByUserId(UUID userId);
}
