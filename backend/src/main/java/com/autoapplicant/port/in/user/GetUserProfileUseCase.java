package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;

import java.util.Optional;
import java.util.UUID;

public interface GetUserProfileUseCase {
    Optional<User> getUser(UUID userId);
    Optional<Profile> getProfile(UUID userId);
}
