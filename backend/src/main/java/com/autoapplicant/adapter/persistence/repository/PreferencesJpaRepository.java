package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.PreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PreferencesJpaRepository extends JpaRepository<PreferencesEntity, UUID> {
    Optional<PreferencesEntity> findByUserId(UUID userId);
}
