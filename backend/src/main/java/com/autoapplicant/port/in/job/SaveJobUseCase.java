package com.autoapplicant.port.in.job;

import java.util.UUID;

public interface SaveJobUseCase {
    void saveJob(UUID userId, UUID jobId);
}
