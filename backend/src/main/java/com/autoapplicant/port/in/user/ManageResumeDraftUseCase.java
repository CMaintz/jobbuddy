package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.ResumeDraft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManageResumeDraftUseCase {
    List<ResumeDraft> getDrafts(UUID userId);
    Optional<ResumeDraft> getDraft(UUID userId, UUID id);
    Optional<ResumeDraft> getDraftByApplication(UUID userId, UUID applicationId);
    ResumeDraft createDraft(UUID userId, ResumeDraft draft);
    ResumeDraft saveDraft(UUID userId, UUID id, ResumeDraft draft);
    ResumeDraft publishDraft(UUID userId, UUID id);
    void deleteDraft(UUID userId, UUID id);
}
