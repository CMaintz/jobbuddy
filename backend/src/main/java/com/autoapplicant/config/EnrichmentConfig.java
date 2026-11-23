package com.autoapplicant.config;

import com.autoapplicant.adapter.ai.AiConfig;
import com.autoapplicant.usecase.job.EnrichmentSweepSettings;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnrichmentConfig {

    /** Postings per call on a CLI agent, where a call costs the same whatever it holds. */
    static final int CLI_BATCH_SIZE = 25;

    @Bean
    public EnrichmentSweepSettings enrichmentSweepSettings(
            @Value("${app.enrichment.max-attempts:4}") int maxAttempts,
            @Value("${app.enrichment.retry-delay:PT6H}") Duration retryDelay,
            @Value("${app.enrichment.call-timeout:PT5M}") Duration callTimeout,
            @Value("${app.enrichment.sweep.delay-ms:250}") long delayMs,
            @Value("${app.enrichment.sweep.concurrency:2}") int concurrency,
            @Value("${app.enrichment.batch.size:0}") int batchSize,
            AppProperties props) {
        return new EnrichmentSweepSettings(maxAttempts, retryDelay, callTimeout, delayMs, concurrency,
                batchSize > 0 ? batchSize : defaultBatchSize(props));
    }

    /** Batching pays on a CLI agent; on an API the cost is tokens, so one posting per call. */
    static int defaultBatchSize(AppProperties props) {
        return AiConfig.isCliProvider(AiConfig.enrichmentChatProviderName(props)) ? CLI_BATCH_SIZE : 1;
    }
}
