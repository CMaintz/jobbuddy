package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.SpokenLanguageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpokenLanguageJpaRepository extends JpaRepository<SpokenLanguageEntity, UUID> {
    List<SpokenLanguageEntity> findByUserIdOrderByDisplayOrder(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
