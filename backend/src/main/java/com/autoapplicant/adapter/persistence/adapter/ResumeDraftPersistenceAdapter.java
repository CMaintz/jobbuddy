package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ResumeDraftEntity;
import com.autoapplicant.adapter.persistence.repository.ResumeDraftJpaRepository;
import com.autoapplicant.domain.user.ResumeDraft;
import com.autoapplicant.port.out.user.ResumeDraftRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ResumeDraftPersistenceAdapter implements ResumeDraftRepositoryPort {

    private final ResumeDraftJpaRepository repo;

    public ResumeDraftPersistenceAdapter(ResumeDraftJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public ResumeDraft save(ResumeDraft draft) {
        ResumeDraftEntity entity = draft.id() != null
                ? repo.findById(draft.id()).orElse(new ResumeDraftEntity())
                : new ResumeDraftEntity();
        if (draft.id() != null) entity.setId(draft.id());
        entity.setUserId(draft.userId());
        entity.setName(draft.name());
        entity.setJobId(draft.jobId());
        entity.setApplicationId(draft.applicationId());
        entity.setResumeData(draft.resumeData() != null ? draft.resumeData() : Map.of());
        entity.setSettings(draft.settings() != null ? draft.settings() : Map.of());
        entity.setStatus(draft.status());
        return toDomain(repo.save(entity));
    }

    @Override
    public List<ResumeDraft> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ResumeDraft> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public Optional<ResumeDraft> findByApplicationIdAndUserId(UUID applicationId, UUID userId) {
        return repo.findByApplicationIdAndUserId(applicationId, userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private ResumeDraft toDomain(ResumeDraftEntity e) {
        return new ResumeDraft(e.getId(), e.getUserId(), e.getName(),
                e.getJobId(), e.getApplicationId(), e.getResumeData(), e.getSettings(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
