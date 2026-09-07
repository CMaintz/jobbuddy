package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.DocumentMapper;
import com.autoapplicant.adapter.persistence.repository.GeneratedDocumentJpaRepository;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentPersistenceAdapter implements GeneratedDocumentRepositoryPort {

    private final GeneratedDocumentJpaRepository repo;

    public DocumentPersistenceAdapter(GeneratedDocumentJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public GeneratedDocument save(GeneratedDocument document) {
        return DocumentMapper.toDomain(repo.save(DocumentMapper.toEntity(document)));
    }

    @Override
    public List<GeneratedDocument> findByApplicationId(UUID applicationId) {
        return repo.findByApplicationId(applicationId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<GeneratedDocument> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<GeneratedDocument> findByJobId(UUID jobId) {
        return repo.findByJobIdOrderByCreatedAtDesc(jobId).stream()
                .map(DocumentMapper::toDomain).toList();
    }

    @Override
    public Optional<GeneratedDocument> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId).map(DocumentMapper::toDomain);
    }

    @Override
    public List<GeneratedDocument> findRecentByUserIdAndType(UUID userId, String documentType, int limit) {
        return repo.findRecentByUserIdAndType(userId, documentType, PageRequest.of(0, limit))
                .stream().map(DocumentMapper::toDomain).toList();
    }
}
