package com.autoapplicant.config;

import com.autoapplicant.usecase.job.EnrichmentSweepSettings;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnrichmentConfig {

    @Bean
    public EnrichmentSweepSettings enrichmentSweepSettings(
            @Value("${app.enrichment.max-attempts:4}") int maxAttempts,
            @Value("${app.enrichment.retry-delay:PT6H}") Duration retryDelay,
            @Value("${app.enrichment.call-timeout:PT5M}") Duration callTimeout,
            @Value("${app.enrichment.sweep.delay-ms:250}") long delayMs,
            @Value("${app.enrichment.sweep.concurrency:2}") int concurrency) {
        return new EnrichmentSweepSettings(maxAttempts, retryDelay, callTimeout, delayMs, concurrency);
    }
}
