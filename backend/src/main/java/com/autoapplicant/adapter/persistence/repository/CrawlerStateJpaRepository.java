package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CrawlerStateEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrawlerStateJpaRepository extends JpaRepository<CrawlerStateEntity, String> {
    Optional<CrawlerStateEntity> findBySource(String source);

    /**
     * Freshens a running crawl's flag and nothing else. A finished crawl is left alone, so a
     * heartbeat that lands just after the end cannot mark it running again.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CrawlerStateEntity s SET s.updatedAt = :at WHERE s.source = :source AND s.isRunning = true")
    int heartbeat(@Param("source") String source, @Param("at") Instant at);
}
