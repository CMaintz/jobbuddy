package com.autoapplicant.adapter.cli;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Drains the enrichment queue, then the embedding queue, as a one-shot CLI command.
 *
 * <p>Usage (from repo root):
 * <pre>
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=enrich"
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=enrich --enrich.limit=100"
 * </pre>
 *
 * <p>Without a limit it runs until both queues are empty, rather than stopping after one batch
 * and asking to be run again — with a backlog in the thousands that meant typing the same
 * command twenty times. Pacing (delay between calls, postings in flight) comes from
 * {@code app.enrichment.sweep.*}, so it matches the scheduled worker instead of hard-coding a
 * rate that was written for a since-changed provider.
 */
@Component
@Profile("enrich")
@Order(2)
public class EnrichmentSweepRunner implements ApplicationRunner {

    private final QueueDrainer drainer;

    public EnrichmentSweepRunner(QueueDrainer drainer) {
        this.drainer = drainer;
    }

    @Override
    public void run(ApplicationArguments args) {
        int limit = args.containsOption("enrich.limit")
                ? Integer.parseInt(args.getOptionValues("enrich.limit").get(0))
                : Integer.MAX_VALUE;

        drainer.drainEnrichment(limit);
        drainer.drainEmbedding();

        System.out.println();
        System.out.println("============================================");
        System.out.println("  Enrichment and embedding queues drained");
        System.out.println("  (see logs/enrichment.log for the detail)");
        System.out.println("============================================");
        System.out.println();
    }
}
