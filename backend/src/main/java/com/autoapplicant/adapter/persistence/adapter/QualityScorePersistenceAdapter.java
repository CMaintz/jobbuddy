package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.DocumentQualityScoreEntity;
import com.autoapplicant.adapter.persistence.repository.DocumentQualityScoreJpaRepository;
import com.autoapplicant.domain.document.QualityScore;
import com.autoapplicant.domain.document.RecordedQualityScore;
import com.autoapplicant.port.out.document.QualityScoreRepositoryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class QualityScorePersistenceAdapter implements QualityScoreRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(QualityScorePersistenceAdapter.class);

    private final DocumentQualityScoreJpaRepository repo;
    private final ObjectMapper objectMapper;

    public QualityScorePersistenceAdapter(DocumentQualityScoreJpaRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecordedQualityScore save(RecordedQualityScore score) {
        DocumentQualityScoreEntity e = new DocumentQualityScoreEntity();
        e.setUserId(score.userId());
        e.setGeneratedDocumentId(score.generatedDocumentId());
        e.setDocumentType(score.documentType());
        e.setTotal(score.score().total());
        e.setDimensions(writeDimensions(score.score()));
        return toDomain(repo.save(e));
    }

    @Override
    public List<RecordedQualityScore> findRecent(UUID userId, int limit) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.max(1, limit)))
                .stream().map(this::toDomain).toList();
    }

    private RecordedQualityScore toDomain(DocumentQualityScoreEntity e) {
        return new RecordedQualityScore(e.getId(), e.getUserId(), e.getGeneratedDocumentId(),
                e.getDocumentType(), new QualityScore(e.getTotal(), readDimensions(e.getDimensions())),
                e.getCreatedAt());
    }

    private String writeDimensions(QualityScore score) {
        try {
            return objectMapper.writeValueAsString(score.dimensions());
        } catch (Exception ex) {
            // The total is the number that matters; losing the breakdown must not lose the score.
            log.warn("Could not serialise quality dimensions: {}", ex.getMessage());
            return "[]";
        }
    }

    private List<QualityScore.Dimension> readDimensions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<QualityScore.Dimension>>() {});
        } catch (Exception ex) {
            // A row written by an older rubric is still worth its total.
            log.debug("Could not read quality dimensions: {}", ex.getMessage());
            return List.of();
        }
    }
}
