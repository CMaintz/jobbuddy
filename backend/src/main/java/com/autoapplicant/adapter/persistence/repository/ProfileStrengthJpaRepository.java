package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ProfileStrengthEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileStrengthJpaRepository extends JpaRepository<ProfileStrengthEntity, UUID> {
    List<ProfileStrengthEntity> findByUserIdOrderByDisplayOrderAsc(UUID userId);
    Optional<ProfileStrengthEntity> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
