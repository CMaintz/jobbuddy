package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.AiUsageRecord;
import com.autoapplicant.domain.ai.AiUsageSummary;
import com.autoapplicant.port.in.ai.GetAiUsageUseCase;
import com.autoapplicant.port.out.ai.AiUsageRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AiUsageService implements GetAiUsageUseCase {

    private final AiUsageRepositoryPort usageRepo;
    private final int dailyTokenLimit;

    public AiUsageService(AiUsageRepositoryPort usageRepo,
                          @Value("${app.ai.daily-token-limit:500000}") int dailyTokenLimit) {
        this.usageRepo = usageRepo;
        this.dailyTokenLimit = dailyTokenLimit;
    }

    @Override
    public AiUsageSummary getUsageSummary(UUID userId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return new AiUsageSummary(
                usageRepo.totalTokens(userId),
                0, // totalTokensOut tracked as part of totalTokens
                usageRepo.totalRequests(userId),
                usageRepo.countTokensSince(userId, startOfDay),
                0,
                usageRepo.countRequestsSince(userId, startOfDay),
                dailyTokenLimit
        );
    }

    public void trackUsage(UUID userId, String model, int tokensIn, int tokensOut, String operation) {
        usageRepo.save(new AiUsageRecord(null, userId, model, tokensIn, tokensOut, operation, null));
    }

    public boolean isOverDailyLimit(UUID userId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return usageRepo.countTokensSince(userId, startOfDay) >= dailyTokenLimit;
    }
}
