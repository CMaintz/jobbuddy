package com.autoapplicant.port.out.job;

import java.util.List;
import java.util.UUID;

public interface SavedJobRepositoryPort {
    void save(UUID userId, UUID jobId);
    void unsave(UUID userId, UUID jobId);
    List<UUID> findJobIdsByUserId(UUID userId);
    boolean isSaved(UUID userId, UUID jobId);
}
