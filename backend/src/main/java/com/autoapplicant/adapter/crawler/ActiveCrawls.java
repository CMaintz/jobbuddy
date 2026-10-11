package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Answers "is a crawl running right now?" from the persisted running flags, ignoring flags that
 * have gone stale, and clears leftover flags when the app starts.
 */
@Component
public class ActiveCrawls {

    private static final Logger log = LoggerFactory.getLogger(ActiveCrawls.class);

    private final CrawlerStateRepositoryPort crawlerStateRepo;
    private final Duration staleRunningAfter;

    public ActiveCrawls(CrawlerStateRepositoryPort crawlerStateRepo,
                        @Value("${app.crawler.stale-running-after:PT2H}") Duration staleRunningAfter) {
        this.crawlerStateRepo = crawlerStateRepo;
        this.staleRunningAfter = staleRunningAfter;
    }

    /** True while any source is mid-crawl. */
    public boolean anyRunning() {
        Instant now = Instant.now();
        return crawlerStateRepo.findAll().stream()
                .anyMatch(state -> state.isActivelyRunning(now, staleRunningAfter));
    }

    /**
     * Clears every running flag on startup. No crawl can be running inside a JVM that has only
     * just started, so any flag still set was left by a crawl the previous process never
     * finished. This assumes a single-instance deployment: with several instances sharing the
     * database, one starting up would clear flags another is legitimately holding.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void clearLeftoverRunningFlags() {
        for (CrawlerState state : crawlerStateRepo.findAll()) {
            if (state.isRunning()) {
                log.warn("Clearing running flag left over from an unfinished crawl: {}",
                        state.source());
                crawlerStateRepo.save(state.notRunning());
            }
        }
    }
}
