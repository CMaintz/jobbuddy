package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.AiUsageSummary;
import com.autoapplicant.port.in.ai.GetAiUsageUseCase;
import com.autoapplicant.port.out.ai.AiUsageRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AiUsageService implements GetAiUsageUseCase {

    private final AiUsageRepositoryPort usageRepo;

    public AiUsageService(AiUsageRepositoryPort usageRepo) {
        this.usageRepo = usageRepo;
    }

    @Override
    public AiUsageSummary getUsageSummary(UUID userId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return new AiUsageSummary(
                usageRepo.totalsSince(userId, null),
                usageRepo.totalsSince(userId, startOfDay),
                usageRepo.byOperation(userId));
    }
}
