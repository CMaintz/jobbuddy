package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.crawler.CrawlerState;

import java.util.List;
import java.util.Optional;

public interface CrawlerStateRepositoryPort {
    CrawlerState save(CrawlerState state);
    Optional<CrawlerState> findBySource(String source);
    List<CrawlerState> findAll();
}
