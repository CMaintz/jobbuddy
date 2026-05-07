package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.Profile;

import java.util.Optional;
import java.util.UUID;

public interface GetUserProfileUseCase {
    Optional<Profile> getProfile(UUID userId);
}
