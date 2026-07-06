package com.autoapplicant.domain.ai;

public record AiUsageSummary(
        int totalTokensIn,
        int totalTokensOut,
        int totalRequests,
        int dailyTokensIn,
        int dailyTokensOut,
        int dailyRequests,
        int dailyLimit
) {}
