package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.ai.AiUsageRecord;

import java.time.Instant;
import java.util.UUID;

public interface AiUsageRepositoryPort {
    AiUsageRecord save(AiUsageRecord record);
    int countTokensSince(UUID userId, Instant since);
    int countRequestsSince(UUID userId, Instant since);
    int totalTokens(UUID userId);
    int totalRequests(UUID userId);
}
