package com.autoapplicant.domain.job;

import java.time.Instant;

public record RawJobData(
        JobSource source,
        String sourceJobId,
        String url,
        String rawHtml,
        String rawJson,
        Instant scrapedAt
) {}
