package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.PromptTemplateFavoriteEntity;
import com.autoapplicant.adapter.persistence.mapper.DocumentMapper;
import com.autoapplicant.adapter.persistence.repository.PromptTemplateFavoriteJpaRepository;
import com.autoapplicant.adapter.persistence.repository.PromptTemplateJpaRepository;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PromptTemplatePersistenceAdapter implements PromptTemplateRepositoryPort {

    private final PromptTemplateJpaRepository repo;
    private final PromptTemplateFavoriteJpaRepository favoriteRepo;

    public PromptTemplatePersistenceAdapter(PromptTemplateJpaRepository repo,
                                            PromptTemplateFavoriteJpaRepository favoriteRepo) {
        this.repo = repo;
        this.favoriteRepo = favoriteRepo;
    }

    @Override
    public PromptTemplate save(PromptTemplate template) {
        return DocumentMapper.toDomain(repo.save(DocumentMapper.toEntity(template)));
    }

    @Override
    public void deleteById(UUID id) {
        repo.deleteById(id);
    }

    @Override
    public Optional<PromptTemplate> findById(UUID id) {
        return repo.findById(id).map(DocumentMapper::toDomain);
    }

    @Override
    public List<PromptTemplate> findByUserId(UUID userId) {
        return repo.findByUserIdOrSystem(userId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<PromptTemplate> findPublic() {
        return repo.findByIsPublicTrueOrderByCreatedAtDesc().stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<PromptTemplate> findSystemDefault(String category) {
        return repo.findFirstByCategoryAndIsSystemTrueOrderByIsDefaultDescCreatedAtAsc(category)
                .map(DocumentMapper::toDomain);
    }

    @Override
    @Transactional
    public void incrementUsage(UUID templateId) {
        repo.incrementUsage(templateId);
    }

    @Override
    public Set<UUID> findFavouriteTemplateIds(UUID userId) {
        return new HashSet<>(favoriteRepo.findTemplateIdsByUserId(userId));
    }

    @Override
    @Transactional
    public void addFavourite(UUID userId, UUID templateId) {
        if (favoriteRepo.existsByUserIdAndTemplateId(userId, templateId)) return;
        PromptTemplateFavoriteEntity fav = new PromptTemplateFavoriteEntity();
        fav.setUserId(userId);
        fav.setTemplateId(templateId);
        favoriteRepo.save(fav);
    }

    @Override
    @Transactional
    public void removeFavourite(UUID userId, UUID templateId) {
        favoriteRepo.deleteByUserIdAndTemplateId(userId, templateId);
    }
}
