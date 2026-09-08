package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

public record ProfileEmbedding(
        UUID id,
        UUID userId,
        float[] embedding,
        String model,
        Instant createdAt
) {}
