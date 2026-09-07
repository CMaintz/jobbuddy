package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ApplicationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationJpaRepository extends JpaRepository<ApplicationEntity, UUID> {
    List<ApplicationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<ApplicationEntity> findByUserId(UUID userId, Pageable pageable);
    Optional<ApplicationEntity> findByIdAndUserId(UUID id, UUID userId);
}
