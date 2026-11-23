package com.autoapplicant.domain.crawler;

import com.autoapplicant.domain.job.JobSource;
import java.util.List;

/**
 * What one blocking crawl run did, as far as its caller needs to judge success.
 *
 * <p>Built from the run itself rather than read back from {@code crawler_state}: with a shared
 * database another process may crawl at the same time, and its errors are not this run's.
 *
 * @param failedSources sources this run started whose crawl ended in an error, or that were
 *     asked for but have no connector; empty when every source finished
 */
public record CrawlRunReport(List<JobSource> failedSources) {

    public CrawlRunReport {
        failedSources = List.copyOf(failedSources);
    }

    public boolean anyFailed() {
        return !failedSources.isEmpty();
    }
}
