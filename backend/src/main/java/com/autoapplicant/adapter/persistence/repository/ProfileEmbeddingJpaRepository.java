package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ProfileEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileEmbeddingJpaRepository extends JpaRepository<ProfileEmbeddingEntity, UUID> {
    Optional<ProfileEmbeddingEntity> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
