package com.autoapplicant.adapter.cli;

import com.autoapplicant.adapter.ai.EmbeddingKeyGuard;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fails the crawl before it starts when the stored embedding provider has no key in the
 * workflow's environment. The thrown exception fails startup, so the run exits non-zero with a
 * message naming the provider and the secret to set, instead of crawling, failing every
 * posting's embedding one by one, and leaving nothing searchable.
 */
@Component
@Profile("crawler")
@Order(1)
public class EmbeddingKeyCheck implements ApplicationRunner {

    private final EmbeddingKeyGuard guard;

    public EmbeddingKeyCheck(EmbeddingKeyGuard guard) {
        this.guard = guard;
    }

    @Override
    public void run(ApplicationArguments args) {
        guard.requireKey();
    }
}
