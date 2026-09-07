package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ProfilePrivateInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfilePrivateInfoJpaRepository extends JpaRepository<ProfilePrivateInfoEntity, UUID> {
    Optional<ProfilePrivateInfoEntity> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
