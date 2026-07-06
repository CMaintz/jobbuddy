package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CrawlerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrawlerStateJpaRepository extends JpaRepository<CrawlerStateEntity, String> {
    Optional<CrawlerStateEntity> findBySource(String source);
}
