package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.ProfileSocial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileSocialRepositoryPort {
    ProfileSocial save(ProfileSocial social);
    List<ProfileSocial> findByUserId(UUID userId);
    Optional<ProfileSocial> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
