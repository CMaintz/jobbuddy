package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ProfileSocialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileSocialJpaRepository extends JpaRepository<ProfileSocialEntity, UUID> {
    List<ProfileSocialEntity> findByUserIdOrderByDisplayOrderAsc(UUID userId);
    Optional<ProfileSocialEntity> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
