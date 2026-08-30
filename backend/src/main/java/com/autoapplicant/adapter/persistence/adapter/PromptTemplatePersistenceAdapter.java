package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.PromptTemplateFavoriteEntity;
import com.autoapplicant.adapter.persistence.mapper.DocumentMapper;
import com.autoapplicant.adapter.persistence.repository.PromptTemplateFavoriteJpaRepository;
import com.autoapplicant.adapter.persistence.entity.UserDefaultPromptEntity;
import com.autoapplicant.adapter.persistence.repository.PromptTemplateJpaRepository;
import com.autoapplicant.adapter.persistence.repository.UserDefaultPromptJpaRepository;
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
    private final UserDefaultPromptJpaRepository userDefaultRepo;

    public PromptTemplatePersistenceAdapter(PromptTemplateJpaRepository repo,
                                            PromptTemplateFavoriteJpaRepository favoriteRepo,
                                            UserDefaultPromptJpaRepository userDefaultRepo) {
        this.repo = repo;
        this.favoriteRepo = favoriteRepo;
        this.userDefaultRepo = userDefaultRepo;
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
    public Optional<PromptTemplate> findDefaultFor(UUID userId, String category) {
        if (userId != null) {
            Optional<PromptTemplate> chosen = userDefaultRepo
                    .findByUserIdAndCategory(userId, category)
                    .map(UserDefaultPromptEntity::getTemplateId)
                    .flatMap(repo::findById)
                    .map(DocumentMapper::toDomain)
                    // A template the user no longer owns (deleted, or never theirs) must not
                    // silently become nobody's prompt — fall through to the app's default.
                    .filter(t -> t.isSystem() || userId.equals(t.userId()));
            if (chosen.isPresent()) return chosen;
        }
        return findSystemDefault(category);
    }

    @Override
    @Transactional
    public void setUserDefault(UUID userId, String category, UUID templateId) {
        UserDefaultPromptEntity e = userDefaultRepo.findByUserIdAndCategory(userId, category)
                .orElseGet(UserDefaultPromptEntity::new);
        e.setUserId(userId);
        e.setCategory(category);
        e.setTemplateId(templateId);
        userDefaultRepo.save(e);
    }

    @Override
    @Transactional
    public void clearUserDefault(UUID userId, String category) {
        userDefaultRepo.deleteByUserIdAndCategory(userId, category);
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
