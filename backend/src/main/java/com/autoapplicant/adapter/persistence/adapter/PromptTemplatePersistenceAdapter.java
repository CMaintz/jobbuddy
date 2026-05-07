package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.DocumentMapper;
import com.autoapplicant.adapter.persistence.repository.PromptTemplateJpaRepository;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PromptTemplatePersistenceAdapter implements PromptTemplateRepositoryPort {

    private final PromptTemplateJpaRepository repo;

    public PromptTemplatePersistenceAdapter(PromptTemplateJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public PromptTemplate save(PromptTemplate template) {
        return DocumentMapper.toDomain(repo.save(DocumentMapper.toEntity(template)));
    }

    @Override
    public Optional<PromptTemplate> findById(UUID id) {
        return repo.findById(id).map(DocumentMapper::toDomain);
    }

    @Override
    public List<PromptTemplate> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<PromptTemplate> findPublic() {
        return repo.findByIsPublicTrueOrderByCreatedAtDesc().stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }
}
