package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CertificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CertificationJpaRepository extends JpaRepository<CertificationEntity, UUID> {
    List<CertificationEntity> findByUserIdOrderByIssuedAtDesc(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
