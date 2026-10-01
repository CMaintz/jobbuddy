package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.SpokenLanguageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpokenLanguageJpaRepository extends JpaRepository<SpokenLanguageEntity, UUID> {
    List<SpokenLanguageEntity> findByUserIdOrderByDisplayOrder(UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
