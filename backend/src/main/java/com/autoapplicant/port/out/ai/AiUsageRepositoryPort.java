package com.autoapplicant.port.out.ai;

import com.autoapplicant.domain.ai.AiOperationUsage;
import com.autoapplicant.domain.ai.AiUsageRecord;
import com.autoapplicant.domain.ai.AiUsageTotals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiUsageRepositoryPort {

    AiUsageRecord save(AiUsageRecord record);

    /** Totals for the user; {@code since} null means all time. */
    AiUsageTotals totalsSince(UUID userId, Instant since);

    /** The same totals split by operation label, heaviest first. */
    List<AiOperationUsage> byOperation(UUID userId);
}
