package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileSocialEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileSocialJpaRepository;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProfileSocialPersistenceAdapter implements ProfileSocialRepositoryPort {

    private final ProfileSocialJpaRepository repo;

    public ProfileSocialPersistenceAdapter(ProfileSocialJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ProfileSocial save(ProfileSocial social) {
        ProfileSocialEntity entity = social.id() != null
                ? repo.findById(social.id()).orElse(new ProfileSocialEntity())
                : new ProfileSocialEntity();
        if (social.id() != null) entity.setId(social.id());
        entity.setUserId(social.userId());
        entity.setPlatform(social.platform());
        entity.setUrl(social.url());
        entity.setUsername(social.username());
        entity.setIconKey(social.iconKey());
        entity.setDisplayOrder(social.displayOrder());
        return toDomain(repo.save(entity));
    }

    @Override
    public List<ProfileSocial> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrderAsc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ProfileSocial> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private ProfileSocial toDomain(ProfileSocialEntity e) {
        return new ProfileSocial(e.getId(), e.getUserId(), e.getPlatform(), e.getUrl(),
                e.getUsername(), e.getIconKey(), e.getDisplayOrder(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
