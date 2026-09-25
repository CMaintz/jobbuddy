package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.CvSectionPromptsEntity;
import com.autoapplicant.adapter.persistence.repository.CvSectionPromptsJpaRepository;
import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.CvSectionPrompts;
import com.autoapplicant.port.out.document.CvSectionPromptsRepositoryPort;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CvSectionPromptsPersistenceAdapter implements CvSectionPromptsRepositoryPort {

    private final CvSectionPromptsJpaRepository repo;

    public CvSectionPromptsPersistenceAdapter(CvSectionPromptsJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<CvSectionPrompts> findByUserId(UUID userId) {
        return repo.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public CvSectionPrompts save(UUID userId, Map<CvSection, String> prompts) {
        CvSectionPromptsEntity entity = repo.findByUserId(userId).orElseGet(CvSectionPromptsEntity::new);
        entity.setUserId(userId);
        entity.setPrompts(toStorage(prompts));
        return toDomain(repo.save(entity));
    }

    private CvSectionPrompts toDomain(CvSectionPromptsEntity e) {
        Map<CvSection, String> prompts = new EnumMap<>(CvSection.class);
        if (e.getPrompts() != null) {
            e.getPrompts().forEach((key, value) ->
                    CvSection.fromKey(key)
                            .filter(section -> value != null && !value.isBlank())
                            .ifPresent(section -> prompts.put(section, value)));
        }
        return new CvSectionPrompts(e.getId(), e.getUserId(), prompts, e.getUpdatedAt());
    }

    /** Drop blank entries so an empty box clears a section rather than storing whitespace. */
    private Map<String, String> toStorage(Map<CvSection, String> prompts) {
        Map<String, String> stored = new LinkedHashMap<>();
        if (prompts != null) {
            prompts.forEach((section, value) -> {
                if (value != null && !value.isBlank()) {
                    stored.put(section.key(), value);
                }
            });
        }
        return stored;
    }
}
