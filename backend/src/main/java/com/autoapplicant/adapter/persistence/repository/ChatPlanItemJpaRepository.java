package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.ChatPlanItemEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChatPlanItemJpaRepository extends JpaRepository<ChatPlanItemEntity, UUID> {

    List<ChatPlanItemEntity> findByPlanIdInOrderByPositionAsc(Collection<UUID> planIds);

    /** The state check is in the WHERE, so Postgres lets one of two racing updates through. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE ChatPlanItemEntity i SET i.state = :to, i.decidedAt = :at"
            + " WHERE i.id = :id AND i.state = :from")
    int move(@Param("id") UUID id, @Param("from") String from, @Param("to") String to,
             @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE ChatPlanItemEntity i SET i.state = :state, i.failure = :failure,"
            + " i.finishedAt = :at WHERE i.id = :id AND i.state = 'RUNNING'")
    int finish(@Param("id") UUID id, @Param("state") String state,
               @Param("failure") String failure, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE ChatPlanItemEntity i SET i.state = 'FAILED', i.failure = :failure,"
            + " i.finishedAt = :at WHERE i.state = 'RUNNING'")
    int failAllRunning(@Param("failure") String failure, @Param("at") Instant at);
}
