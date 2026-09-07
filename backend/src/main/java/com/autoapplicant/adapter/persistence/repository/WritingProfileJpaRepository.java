package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.WritingProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WritingProfileJpaRepository extends JpaRepository<WritingProfileEntity, UUID> {
    Optional<WritingProfileEntity> findByUserId(UUID userId);
}
