package com.autoapplicant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Discards work the pool cannot take, and says so.
 *
 * <p>{@link ThreadPoolExecutor.DiscardPolicy} is a no-op method body: when a queue fills, tasks
 * vanish and the log stays clean, which reads exactly like everything working. During a large
 * crawl that silence covered thousands of dropped enrichments.
 *
 * <p>Logs the first drop immediately and then every {@link #LOG_EVERY} after it, so a saturated
 * pool is obvious without a line per task.
 */
public class CountingDiscardPolicy implements RejectedExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(CountingDiscardPolicy.class);
    private static final long LOG_EVERY = 100;

    private final String poolName;
    private final AtomicLong discarded = new AtomicLong();

    public CountingDiscardPolicy(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        long total = discarded.incrementAndGet();
        if (total == 1 || total % LOG_EVERY == 0) {
            log.warn("{}: queue full ({} queued, {} active) — discarded {} task(s) so far. "
                     + "Discarded enrichment is recoverable: the posting stays PENDING and the "
                     + "worker picks it up.",
                    poolName, executor.getQueue().size(), executor.getActiveCount(), total);
        }
    }

    /** Total discards since startup — surfaced so a health check can read it. */
    public long discardedCount() {
        return discarded.get();
    }
}
