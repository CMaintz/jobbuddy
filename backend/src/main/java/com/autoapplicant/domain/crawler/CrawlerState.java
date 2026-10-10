package com.autoapplicant.domain.crawler;

import java.time.Duration;
import java.time.Instant;

public record CrawlerState(
        String source,
        int pageOffset,
        Instant lastCrawlStartedAt,
        Instant lastCrawlFinishedAt,
        int jobsFound,
        int jobsIngested,
        String lastError,
        boolean isRunning,
        Instant updatedAt
) {

    /**
     * True when this source is running and the flag is recent enough to trust. A flag older
     * than {@code staleAfter} is taken as left behind by a crawl that never finished (a crash,
     * a redeploy), so it must not block enrichment or a new crawl forever.
     */
    public boolean isActivelyRunning(Instant now, Duration staleAfter) {
        Instant lastTouched = updatedAt != null ? updatedAt : lastCrawlStartedAt;
        return isRunning && lastTouched != null && lastTouched.isAfter(now.minus(staleAfter));
    }

    /** The same state with the running flag cleared. */
    public CrawlerState notRunning() {
        return new CrawlerState(source, pageOffset, lastCrawlStartedAt, lastCrawlFinishedAt,
                jobsFound, jobsIngested, lastError, false, updatedAt);
    }
}
