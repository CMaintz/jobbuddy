package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.ResumeDraft;
import com.autoapplicant.port.in.user.ManageResumeDraftUseCase;
import com.autoapplicant.port.out.user.ResumeDraftRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResumeDraftService implements ManageResumeDraftUseCase {

    private final ResumeDraftRepositoryPort repo;

    public ResumeDraftService(ResumeDraftRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<ResumeDraft> getDrafts(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public Optional<ResumeDraft> getDraft(UUID userId, UUID id) {
        return repo.findByIdAndUserId(id, userId);
    }

    @Override
    public Optional<ResumeDraft> getDraftByApplication(UUID userId, UUID applicationId) {
        return repo.findByApplicationIdAndUserId(applicationId, userId);
    }

    @Override
    public ResumeDraft createDraft(UUID userId, ResumeDraft draft) {
        ResumeDraft toSave = new ResumeDraft(null, userId,
                draft.name() != null ? draft.name() : "My Resume",
                draft.jobId(), draft.applicationId(),
                draft.resumeData() != null ? draft.resumeData() : Map.of(),
                draft.settings() != null ? draft.settings() : Map.of(),
                "DRAFT", null, null);
        return repo.save(toSave);
    }

    @Override
    public ResumeDraft saveDraft(UUID userId, UUID id, ResumeDraft draft) {
        ResumeDraft existing = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        ResumeDraft updated = new ResumeDraft(id, userId,
                draft.name() != null ? draft.name() : existing.name(),
                draft.jobId() != null ? draft.jobId() : existing.jobId(),
                draft.applicationId() != null ? draft.applicationId() : existing.applicationId(),
                draft.resumeData() != null ? draft.resumeData() : existing.resumeData(),
                draft.settings() != null ? draft.settings() : existing.settings(),
                existing.status(), null, null);
        return repo.save(updated);
    }

    @Override
    public ResumeDraft publishDraft(UUID userId, UUID id) {
        ResumeDraft existing = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        ResumeDraft published = new ResumeDraft(id, userId, existing.name(),
                existing.jobId(), existing.applicationId(),
                existing.resumeData(), existing.settings(),
                "PUBLISHED", null, null);
        return repo.save(published);
    }

    @Override
    public void deleteDraft(UUID userId, UUID id) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
