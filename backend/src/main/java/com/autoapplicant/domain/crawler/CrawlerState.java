package com.autoapplicant.domain.crawler;

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
) {}
