package com.autoapplicant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Keep concurrency low to stay within AI provider rate limits.
        // Free-tier Gemini allows ~15 RPM; 2 threads with retry backoff keeps us safe.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("ai-");
        // Discard enrichment tasks when the queue is full rather than blocking the
        // crawler thread. Jobs are already saved as titled drafts at this point;
        // the enrichment sweep will pick them up later.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        // Wait for queued enrichment tasks to complete before JVM exits (CLI mode).
        // Without this, Spring closes the context while thousands of enrichment tasks
        // are still queued, resulting in silent data loss.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(7200); // up to 2h for large crawl batches
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
