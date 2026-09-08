package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.AiUsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AiUsageLogJpaRepository extends JpaRepository<AiUsageLogEntity, UUID> {

    @Query("SELECT COALESCE(SUM(e.tokensIn + e.tokensOut), 0) FROM AiUsageLogEntity e WHERE e.userId = :userId AND e.createdAt >= :since")
    int countTokensSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM AiUsageLogEntity e WHERE e.userId = :userId AND e.createdAt >= :since")
    int countRequestsSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(e.tokensIn + e.tokensOut), 0) FROM AiUsageLogEntity e WHERE e.userId = :userId")
    int totalTokens(@Param("userId") UUID userId);

    @Query("SELECT COUNT(e) FROM AiUsageLogEntity e WHERE e.userId = :userId")
    int totalRequests(@Param("userId") UUID userId);
}
