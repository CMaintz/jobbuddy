package com.autoapplicant.usecase.job;

import java.time.Duration;

/**
 * How the enrichment sweep paces itself and when it gives up on a posting.
 *
 * @param maxAttempts a posting is given up on after this many failed attempts
 * @param retryDelay how long a failed posting is left alone before it is tried again
 * @param callTimeout how long to wait on one AI call before deciding the provider is saturated
 * @param delayMs courtesy gap between windows, in milliseconds
 * @param concurrency postings (or batches) in flight at once, capped at 8
 */
public record EnrichmentSweepSettings(int maxAttempts, Duration retryDelay, Duration callTimeout,
                                      long delayMs, int concurrency) {

    public EnrichmentSweepSettings {
        delayMs = Math.max(0, delayMs);
        // Capped: the provider pool is two threads deep, and asking for more just queues.
        concurrency = Math.max(1, Math.min(concurrency, 8));
    }
}
