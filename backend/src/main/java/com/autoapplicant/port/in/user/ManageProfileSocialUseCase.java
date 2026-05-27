package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.ProfileSocial;

import java.util.List;
import java.util.UUID;

public interface ManageProfileSocialUseCase {
    List<ProfileSocial> getSocials(UUID userId);
    ProfileSocial addSocial(UUID userId, ProfileSocial social);
    ProfileSocial updateSocial(UUID userId, UUID id, ProfileSocial social);
    void deleteSocial(UUID userId, UUID id);
    List<ProfileSocial> reorderSocials(UUID userId, List<ProfileSocial> ordered);
}
