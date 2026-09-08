package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.AiUsageLogEntity;
import com.autoapplicant.adapter.persistence.repository.AiUsageLogJpaRepository;
import com.autoapplicant.domain.ai.AiUsageRecord;
import com.autoapplicant.port.out.ai.AiUsageRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AiUsagePersistenceAdapter implements AiUsageRepositoryPort {

    private final AiUsageLogJpaRepository repo;

    public AiUsagePersistenceAdapter(AiUsageLogJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public AiUsageRecord save(AiUsageRecord record) {
        AiUsageLogEntity e = new AiUsageLogEntity();
        e.setUserId(record.userId());
        e.setModel(record.model());
        e.setTokensIn(record.tokensIn());
        e.setTokensOut(record.tokensOut());
        e.setOperation(record.operation());
        AiUsageLogEntity saved = repo.save(e);
        return new AiUsageRecord(saved.getId(), saved.getUserId(), saved.getModel(),
                saved.getTokensIn(), saved.getTokensOut(), saved.getOperation(), saved.getCreatedAt());
    }

    @Override
    public int countTokensSince(UUID userId, Instant since) {
        return repo.countTokensSince(userId, since);
    }

    @Override
    public int countRequestsSince(UUID userId, Instant since) {
        return repo.countRequestsSince(userId, since);
    }

    @Override
    public int totalTokens(UUID userId) {
        return repo.totalTokens(userId);
    }

    @Override
    public int totalRequests(UUID userId) {
        return repo.totalRequests(userId);
    }
}
