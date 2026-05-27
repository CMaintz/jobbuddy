package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.ProfilePrivateInfo;

import java.util.Optional;
import java.util.UUID;

public interface ProfilePrivateInfoRepositoryPort {
    ProfilePrivateInfo save(ProfilePrivateInfo info);
    Optional<ProfilePrivateInfo> findByUserId(UUID userId);
}
