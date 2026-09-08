package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.AiUsageLogEntity;
import com.autoapplicant.domain.ai.AiOperationUsage;
import com.autoapplicant.domain.ai.AiUsageTotals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiUsageLogJpaRepository extends JpaRepository<AiUsageLogEntity, UUID> {

    @Query("""
            SELECT new com.autoapplicant.domain.ai.AiUsageTotals(
                       COALESCE(SUM(e.tokensIn), 0), COALESCE(SUM(e.tokensOut), 0), COUNT(e))
            FROM AiUsageLogEntity e
            WHERE e.userId = :userId AND (:since IS NULL OR e.createdAt >= :since)
            """)
    AiUsageTotals totalsSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("""
            SELECT new com.autoapplicant.domain.ai.AiOperationUsage(
                       e.operation, COALESCE(SUM(e.tokensIn), 0), COALESCE(SUM(e.tokensOut), 0), COUNT(e))
            FROM AiUsageLogEntity e
            WHERE e.userId = :userId
            GROUP BY e.operation
            ORDER BY SUM(e.tokensIn + e.tokensOut) DESC, COUNT(e) DESC
            """)
    List<AiOperationUsage> byOperation(@Param("userId") UUID userId);
}
