package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.SpokenLanguageEntity;
import com.autoapplicant.adapter.persistence.repository.SpokenLanguageJpaRepository;
import com.autoapplicant.domain.user.SpokenLanguage;
import com.autoapplicant.port.out.user.SpokenLanguageRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SpokenLanguagePersistenceAdapter implements SpokenLanguageRepositoryPort {

    private final SpokenLanguageJpaRepository repo;

    public SpokenLanguagePersistenceAdapter(SpokenLanguageJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public SpokenLanguage save(SpokenLanguage language) {
        return toDomain(repo.save(toEntity(language)));
    }

    @Override
    public List<SpokenLanguage> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByDisplayOrder(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private SpokenLanguageEntity toEntity(SpokenLanguage lang) {
        SpokenLanguageEntity e = new SpokenLanguageEntity();
        e.setId(lang.id());
        e.setUserId(lang.userId());
        e.setLanguage(lang.language());
        e.setProficiency(lang.proficiency());
        e.setDisplayOrder(lang.displayOrder());
        return e;
    }

    private SpokenLanguage toDomain(SpokenLanguageEntity e) {
        return new SpokenLanguage(
                e.getId(), e.getUserId(), e.getLanguage(),
                e.getProficiency(), e.getDisplayOrder(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
