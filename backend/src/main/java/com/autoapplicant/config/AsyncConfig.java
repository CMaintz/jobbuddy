package com.autoapplicant.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * The pool itself, kept as its own bean so Spring still runs its shutdown handling.
     * Work is submitted through {@link #aiTaskExecutor}, which carries the caller's
     * security context across.
     */
    @Bean(name = "aiTaskPool")
    public ThreadPoolTaskExecutor aiTaskPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Keep concurrency low to stay within AI provider rate limits.
        // Free-tier Gemini allows ~15 RPM; 2 threads with retry backoff keeps us safe.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("ai-");
        // Nothing should reach this now that ingest leaves postings PENDING for the worker
        // rather than submitting them, but a silent DiscardPolicy is how thousands of
        // enrichments disappeared unnoticed. If the pool ever saturates again, it says so.
        executor.setRejectedExecutionHandler(new CountingDiscardPolicy("aiTaskPool"));
        // Wait for queued enrichment tasks to complete before JVM exits (CLI mode).
        // Without this, Spring closes the context while thousands of enrichment tasks
        // are still queued, resulting in silent data loss.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(7200); // up to 2h for large crawl batches
        executor.initialize();
        return executor;
    }

    /**
     * Bulk crawl enrichment. Two things find their user through
     * SecurityContextHolder: the per-user API key and the usage log. A bare thread pool
     * does not carry that context across, which sends every generation through the
     * server's own provider and logs none of it — silently, because "no user" is also
     * what legitimate background work looks like. Wrapping the pool propagates it, which
     * matters here too: a manual job add is enrichment a signed-in user triggered.
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor(@Qualifier("aiTaskPool") ThreadPoolTaskExecutor pool) {
        return new DelegatingSecurityContextAsyncTaskExecutor(pool);
    }

    /**
     * AI work a user is waiting on — generating a document, refining one, a skill-gap
     * report, recomputing a profile embedding after an edit.
     *
     * <p>Deliberately not the enrichment pool. That one is two threads deep behind a
     * queue thousands of crawl tasks long and drops work when full, which is right for
     * enrichment (the sweep picks it up later) and wrong for someone waiting on a cover
     * letter: their request would either sit behind the whole crawl backlog or be
     * discarded outright, surfacing only as a timeout a minute later.
     *
     * <p>CallerRunsPolicy rather than a bigger queue: under load the request thread does
     * the work itself, which is slow but never silently loses it.
     */
    @Bean(name = "userAiTaskPool")
    public ThreadPoolTaskExecutor userAiTaskPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-user-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    @Bean(name = "userAiTaskExecutor")
    public Executor userAiTaskExecutor(@Qualifier("userAiTaskPool") ThreadPoolTaskExecutor pool) {
        return new DelegatingSecurityContextAsyncTaskExecutor(pool);
    }

    /**
     * The pool behind every {@code @Scheduled} method.
     *
     * <p>Spring's default is a single thread, which it never says out loud. Seven scheduled jobs
     * shared it, so one slow or blocked job stopped all of them — and the enrichment sweep, which
     * waits on AI calls, is exactly such a job. Sized so a long-running sweep cannot stop the
     * expiry, URL-check and reminder jobs from running.
     */
    @Bean(name = "taskScheduler")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }

    /**
     * The enrichment worker's own thread. Single, because enrichment is rate-limited by the AI
     * provider rather than by local CPU, and because one drain at a time keeps the progress log
     * readable and the backlog arithmetic honest.
     */
    @Bean(name = "enrichmentWorkerExecutor")
    public Executor enrichmentWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("enrich-worker-");
        executor.setRejectedExecutionHandler(new CountingDiscardPolicy("enrichmentWorker"));
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "crawlerTaskExecutor")
    public Executor crawlerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("crawler-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
