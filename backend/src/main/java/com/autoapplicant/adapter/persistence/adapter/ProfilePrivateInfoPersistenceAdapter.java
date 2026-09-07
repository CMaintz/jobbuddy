package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfilePrivateInfoEntity;
import com.autoapplicant.adapter.persistence.repository.ProfilePrivateInfoJpaRepository;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProfilePrivateInfoPersistenceAdapter implements ProfilePrivateInfoRepositoryPort {

    private final ProfilePrivateInfoJpaRepository repo;

    public ProfilePrivateInfoPersistenceAdapter(ProfilePrivateInfoJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ProfilePrivateInfo save(ProfilePrivateInfo info) {
        ProfilePrivateInfoEntity entity = repo.findByUserId(info.userId())
                .orElse(new ProfilePrivateInfoEntity());
        if (info.id() != null) entity.setId(info.id());
        entity.setUserId(info.userId());
        entity.setFullName(info.fullName());
        entity.setPhone(info.phone());
        entity.setPhotoUrl(info.photoUrl());
        entity.setLocation(info.location());
        entity.setMunicipality(info.municipality());
        entity.setContactEmail(info.contactEmail());
        ProfilePrivateInfoEntity saved = repo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ProfilePrivateInfo> findByUserId(UUID userId) {
        return repo.findByUserId(userId).map(this::toDomain);
    }

    private ProfilePrivateInfo toDomain(ProfilePrivateInfoEntity e) {
        return new ProfilePrivateInfo(e.getId(), e.getUserId(), e.getFullName(),
                e.getPhone(), e.getPhotoUrl(), e.getLocation(), e.getMunicipality(),
                e.getContactEmail(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
