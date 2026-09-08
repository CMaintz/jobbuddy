package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.ProfilePrivateInfo;

import java.util.UUID;

public interface ManageProfilePrivateInfoUseCase {
    ProfilePrivateInfo getPrivateInfo(UUID userId);
    ProfilePrivateInfo updatePrivateInfo(UUID userId, ProfilePrivateInfo info);
    /** Stores the uploaded bytes and updates the user's photo URL in one step. Returns the new photo URL. */
    String uploadPhoto(UUID userId, byte[] bytes, String originalFilename);
}
