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
        String shortDescription,
        /** Application deadline when the source provides it structurally (e.g. Jobnet). Null otherwise. */
        java.time.LocalDate applicationDeadline
) {
    /** Convenience constructor for sources without structured deadlines. */
    public RawJobData(JobSource source, String sourceJobId, String url, String rawHtml, String rawJson,
                      Instant scrapedAt, java.util.List<String> rawCategories, String shortDescription) {
        this(source, sourceJobId, url, rawHtml, rawJson, scrapedAt, rawCategories, shortDescription, null);
    }
}
