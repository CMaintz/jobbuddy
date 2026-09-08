package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileEmbeddingEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileEmbeddingJpaRepository;
import com.autoapplicant.domain.user.ProfileEmbedding;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProfileEmbeddingPersistenceAdapter implements ProfileEmbeddingRepositoryPort {

    private final ProfileEmbeddingJpaRepository repo;

    public ProfileEmbeddingPersistenceAdapter(ProfileEmbeddingJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ProfileEmbedding save(ProfileEmbedding embedding) {
        ProfileEmbeddingEntity e = repo.findByUserId(embedding.userId())
                .orElse(new ProfileEmbeddingEntity());
        e.setUserId(embedding.userId());
        e.setEmbedding(embedding.embedding());
        e.setModel(embedding.model());
        ProfileEmbeddingEntity saved = repo.save(e);
        return toDomain(saved);
    }

    @Override
    public Optional<ProfileEmbedding> findByUserId(UUID userId) {
        return repo.findByUserId(userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        repo.deleteByUserId(userId);
    }

    private ProfileEmbedding toDomain(ProfileEmbeddingEntity e) {
        return new ProfileEmbedding(e.getId(), e.getUserId(), e.getEmbedding(),
                e.getModel(), e.getCreatedAt());
    }
}
