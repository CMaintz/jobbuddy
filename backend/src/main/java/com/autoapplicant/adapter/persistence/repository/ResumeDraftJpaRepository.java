package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ResumeDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeDraftJpaRepository extends JpaRepository<ResumeDraftEntity, UUID> {
    List<ResumeDraftEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<ResumeDraftEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<ResumeDraftEntity> findByApplicationIdAndUserId(UUID applicationId, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
