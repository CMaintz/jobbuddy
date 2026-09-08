package com.autoapplicant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /**
     * The application clock. Injected rather than called statically so time-dependent behaviour —
     * follow-up scheduling, staleness windows — can be tested without sleeping or waiting for a
     * date to arrive.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
