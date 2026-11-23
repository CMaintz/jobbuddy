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
     * Clears running flags older than {@code stale-running-after} on startup. A fresh flag is
     * kept even though this JVM has only just started: the database can be shared with another
     * process (the scheduled crawl, or a second app instance) whose crawl is still live, and
     * clearing its flag lets the enrichment and embedding sweeps start alongside that crawl.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void clearLeftoverRunningFlags() {
        Instant now = Instant.now();
        for (CrawlerState state : crawlerStateRepo.findAll()) {
            if (state.isRunning() && !state.isActivelyRunning(now, staleRunningAfter)) {
                log.warn("Clearing running flag left over from an unfinished crawl: {}",
                        state.source());
                crawlerStateRepo.save(state.notRunning());
            }
        }
    }
}
