package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileStrengthEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileStrengthJpaRepository;
import com.autoapplicant.domain.user.ProfileStrength;
import com.autoapplicant.port.out.user.ProfileStrengthRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProfileStrengthPersistenceAdapter implements ProfileStrengthRepositoryPort {

    private final ProfileStrengthJpaRepository repo;

    public ProfileStrengthPersistenceAdapter(ProfileStrengthJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ProfileStrength save(ProfileStrength strength) {
        ProfileStrengthEntity entity = strength.id() != null
                ? repo.findById(strength.id()).orElse(new ProfileStrengthEntity())
                : new ProfileStrengthEntity();
        if (strength.id() != null) entity.setId(strength.id());
        entity.setUserId(strength.userId());
        entity.setTitle(strength.title());
        entity.setDescription(strength.description());
        entity.setIconKey(strength.iconKey());
        entity.setDisplayOrder(strength.displayOrder());
        return toDomain(repo.save(entity));
    }

    @Override
    public List<ProfileStrength> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrderAsc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ProfileStrength> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private ProfileStrength toDomain(ProfileStrengthEntity e) {
        return new ProfileStrength(e.getId(), e.getUserId(), e.getTitle(),
                e.getDescription(), e.getIconKey(), e.getDisplayOrder(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
