package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.ProfileEmbedding;

import java.util.Optional;
import java.util.UUID;

public interface ProfileEmbeddingRepositoryPort {
    ProfileEmbedding save(ProfileEmbedding embedding);
    Optional<ProfileEmbedding> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
