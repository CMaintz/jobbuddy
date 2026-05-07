package com.autoapplicant.port.in.job;

import java.util.UUID;

public interface IgnoreJobUseCase {
    void ignoreJob(UUID userId, UUID jobId, String reason);
}
