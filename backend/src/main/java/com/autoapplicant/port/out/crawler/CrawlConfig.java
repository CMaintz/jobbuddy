package com.autoapplicant.port.out.crawler;

import com.autoapplicant.domain.job.JobSource;

import java.util.List;

public record CrawlConfig(
        JobSource source,
        int maxPages,
        long delayMs,
        List<String> keywords
) {}
