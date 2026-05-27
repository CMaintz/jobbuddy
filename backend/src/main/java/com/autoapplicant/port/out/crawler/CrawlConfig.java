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
        Consumer<RawJobData> onJobFound
) {}
