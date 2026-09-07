package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.ProfileStrength;

import java.util.List;
import java.util.UUID;

public interface ManageProfileStrengthUseCase {
    List<ProfileStrength> getStrengths(UUID userId);
    ProfileStrength addStrength(UUID userId, ProfileStrength strength);
    ProfileStrength updateStrength(UUID userId, UUID id, ProfileStrength strength);
    void deleteStrength(UUID userId, UUID id);
    List<ProfileStrength> reorderStrengths(UUID userId, List<ProfileStrength> ordered);
}
