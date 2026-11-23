package com.autoapplicant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the @Scheduled jobs everywhere except the one-shot crawl. The crawl run calls what it
 * needs (expiry, the URL probe, the drains) itself; anything left ticking in that process would
 * race its drain, and the LinkedIn plan job would call a generation provider it has no login for.
 */
@Configuration
@EnableScheduling
@Profile("!crawler")
public class SchedulingConfig {
}
