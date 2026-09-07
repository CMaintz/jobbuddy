package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.ProfileStrength;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileStrengthRepositoryPort {
    ProfileStrength save(ProfileStrength strength);
    List<ProfileStrength> findByUserId(UUID userId);
    Optional<ProfileStrength> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
