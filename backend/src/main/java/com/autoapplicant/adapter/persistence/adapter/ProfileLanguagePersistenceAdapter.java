package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ProfileLanguageEntity;
import com.autoapplicant.adapter.persistence.repository.ProfileLanguageJpaRepository;
import com.autoapplicant.domain.user.ProfileLanguage;
import com.autoapplicant.port.out.user.ProfileLanguageRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ProfileLanguagePersistenceAdapter implements ProfileLanguageRepositoryPort {

    private final ProfileLanguageJpaRepository repo;

    public ProfileLanguagePersistenceAdapter(ProfileLanguageJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ProfileLanguage save(ProfileLanguage language) {
        return toDomain(repo.save(toEntity(language)));
    }

    @Override
    public List<ProfileLanguage> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrder(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private ProfileLanguageEntity toEntity(ProfileLanguage lang) {
        ProfileLanguageEntity e = new ProfileLanguageEntity();
        e.setId(lang.id());
        e.setUserId(lang.userId());
        e.setLanguage(lang.language());
        e.setProficiency(lang.proficiency());
        e.setDisplayOrder(lang.displayOrder());
        return e;
    }

    private ProfileLanguage toDomain(ProfileLanguageEntity e) {
        return new ProfileLanguage(
                e.getId(), e.getUserId(), e.getLanguage(),
                e.getProficiency(), e.getDisplayOrder(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
