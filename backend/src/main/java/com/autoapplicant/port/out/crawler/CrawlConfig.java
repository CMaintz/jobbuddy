package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public record CrawlConfig(
        JobSource source,
        int maxPages,
        long delayMs,
        List<String> keywords,
        Predicate<String> isKnownGuid,
        Consumer<RawJobData> onJobFound,
        /**
         * When true, the connector will never stop early due to already-known pages.
         * It only stops when the feed returns an empty page or the maxPages ceiling is hit.
         * Use this for first-time backfills or forced re-crawls during development.
         */
        boolean force
) {}
