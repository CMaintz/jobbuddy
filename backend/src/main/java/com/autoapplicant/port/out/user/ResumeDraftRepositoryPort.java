package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.ResumeDraft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeDraftRepositoryPort {
    ResumeDraft save(ResumeDraft draft);
    List<ResumeDraft> findByUserId(UUID userId);
    Optional<ResumeDraft> findByIdAndUserId(UUID id, UUID userId);
    Optional<ResumeDraft> findByApplicationIdAndUserId(UUID applicationId, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
