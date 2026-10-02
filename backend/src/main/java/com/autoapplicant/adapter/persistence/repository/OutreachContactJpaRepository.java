package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.OutreachContactEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutreachContactJpaRepository extends JpaRepository<OutreachContactEntity, UUID> {
    List<OutreachContactEntity> findByUserId(UUID userId);
    Optional<OutreachContactEntity> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    @Query("SELECT e.createdAt FROM OutreachContactEntity e WHERE e.userId = :userId AND e.createdAt >= :since")
    List<Instant> findCreatedAtSince(UUID userId, Instant since);

    @Query("SELECT e.contactedAt FROM OutreachContactEntity e WHERE e.userId = :userId AND e.contactedAt >= :since")
    List<Instant> findContactedAtSince(UUID userId, Instant since);
}
