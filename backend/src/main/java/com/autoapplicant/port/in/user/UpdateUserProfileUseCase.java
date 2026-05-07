package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.Profile;

import java.util.UUID;

public interface UpdateUserProfileUseCase {
    Profile updateProfile(UUID userId, Profile profile);
}
