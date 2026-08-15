package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.RetractedClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetractedClaimJpaRepository extends JpaRepository<RetractedClaimEntity, UUID> {
    List<RetractedClaimEntity> findByUserId(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
