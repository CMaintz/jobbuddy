package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ProfileLanguageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileLanguageJpaRepository extends JpaRepository<ProfileLanguageEntity, UUID> {
    List<ProfileLanguageEntity> findByUserIdOrderByDisplayOrder(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
