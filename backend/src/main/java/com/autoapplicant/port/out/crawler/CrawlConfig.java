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
         * Called for a posting the connector recognised and chose not to re-emit.
         *
         * <p>Skipping a known GUID saves the work of fetching and parsing a detail page, but
         * seeing a posting again is the only evidence we ever get that it is still live:
         * {@code lastSeenAt} is refreshed in the ingest path, and the expiry sweep deactivates
         * anything not seen for {@code app.job.stale-days}. A connector that skips silently is
         * therefore telling the app the posting has vanished. Call this instead — it costs one
         * indexed update and nothing else.
         */
        Consumer<String> onKnownJobSeen,
        /**
         * When true, the connector will never stop early due to already-known pages.
         * It only stops when the feed returns an empty page or the maxPages ceiling is hit.
         * Use this for first-time backfills or forced re-crawls during development.
         */
        boolean force
) {}
