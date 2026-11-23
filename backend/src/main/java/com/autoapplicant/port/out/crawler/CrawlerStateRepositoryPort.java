package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.crawler.CrawlerState;

import java.util.List;
import java.util.Optional;

public interface CrawlerStateRepositoryPort {
    CrawlerState save(CrawlerState state);
    Optional<CrawlerState> findBySource(String source);
    List<CrawlerState> findAll();
    /**
     * Marks a running crawl as still alive at {@code at}. Staleness is judged from this, not
     * from when the crawl started. Does nothing for a source that is not running.
     */
    void heartbeat(String source, java.time.Instant at);
}
