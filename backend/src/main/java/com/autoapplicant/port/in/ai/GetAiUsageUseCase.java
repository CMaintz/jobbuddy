package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.ai.AiUsageSummary;

import java.util.UUID;

public interface GetAiUsageUseCase {
    AiUsageSummary getUsageSummary(UUID userId);
}
