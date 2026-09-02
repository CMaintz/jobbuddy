package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.AiUsageLogEntity;
import com.autoapplicant.adapter.persistence.repository.AiUsageLogJpaRepository;
import com.autoapplicant.domain.ai.AiOperationUsage;
import com.autoapplicant.domain.ai.AiUsageRecord;
import com.autoapplicant.domain.ai.AiUsageTotals;
import com.autoapplicant.port.out.ai.AiUsageRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
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
    public AiUsageTotals totalsSince(UUID userId, Instant since) {
        AiUsageTotals totals = repo.totalsSince(userId, since);
        return totals == null ? AiUsageTotals.NONE : totals;
    }

    @Override
    public List<AiOperationUsage> byOperation(UUID userId) {
        return repo.byOperation(userId);
    }
}
