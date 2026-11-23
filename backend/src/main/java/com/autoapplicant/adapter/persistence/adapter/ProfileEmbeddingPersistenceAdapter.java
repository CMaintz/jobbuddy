package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileEmbeddingEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileEmbeddingJpaRepository;
import com.autoapplicant.domain.user.ProfileEmbedding;
import com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProfileEmbeddingPersistenceAdapter implements ProfileEmbeddingRepositoryPort {

    private final ProfileEmbeddingJpaRepository repo;
    private final ResolveEmbeddingChoiceUseCase activeChoice;

    public ProfileEmbeddingPersistenceAdapter(ProfileEmbeddingJpaRepository repo, ResolveEmbeddingChoiceUseCase activeChoice) {
        this.repo = repo;
        this.activeChoice = activeChoice;
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

    /**
     * Empty when her stored vector is from another model than the install's: matching then
     * recomputes it with the current model, and saving replaces the old one.
     */
    @Override
    public Optional<ProfileEmbedding> findByUserId(UUID userId) {
        String model = activeChoice.currentChoice().model();
        return repo.findByUserId(userId).filter(e -> model.equals(e.getModel())).map(this::toDomain);
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
