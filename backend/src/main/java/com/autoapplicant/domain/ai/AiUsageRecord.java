package com.autoapplicant.domain.ai;

import java.time.Instant;
import java.util.UUID;

public record AiUsageRecord(
        UUID id,
        UUID userId,
        String model,
        int tokensIn,
        int tokensOut,
        String operation,
        Instant createdAt
) {}
