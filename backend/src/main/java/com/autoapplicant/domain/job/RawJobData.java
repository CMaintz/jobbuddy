package com.autoapplicant.domain.job;

import java.time.Instant;

public record RawJobData(
        JobSource source,
        String sourceJobId,
        String url,
        String rawHtml,
        String rawJson,
        Instant scrapedAt,
        /** Raw category strings from the source (e.g. RSS {@code <category>} tags). May be empty. */
        java.util.List<String> rawCategories,
        /** Brief teaser for job cards (1-2 sentences). Populated by sources that provide it (e.g. RSS). Null otherwise. */
        String shortDescription
) {}
