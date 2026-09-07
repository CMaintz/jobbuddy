package com.autoapplicant.domain.job;

import java.time.Instant;
import java.util.UUID;

public record JobEmbedding(
        UUID id,
        UUID jobId,
        float[] embedding,
        String model,
        Instant createdAt
) {}
