package com.autoapplicant.adapter.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Drains the enrichment queue, then the embedding queue, as a one-shot CLI command that ends
 * the process when done.
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
public class EnrichmentSweepRunner implements OneShotCommand {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSweepRunner.class);

    private final QueueDrainer drainer;
    private int exitCode;

    public EnrichmentSweepRunner(QueueDrainer drainer) {
        this.drainer = drainer;
    }

    @Override
    public void run(ApplicationArguments args) {
        int limit = args.containsOption("enrich.limit")
                ? Integer.parseInt(args.getOptionValues("enrich.limit").get(0))
                : Integer.MAX_VALUE;

        boolean enriched = drainer.drainEnrichment(limit);
        boolean embedded = drainer.drainEmbedding();
        if (!enriched) {
            log.error("Enrichment failed for every posting in a pass; check the chat provider "
                      + "and its login before the attempts run out");
        }
        if (!embedded) {
            log.error("Embedding failed for every posting in a pass; check the embedding provider "
                      + "and its key before the attempts run out");
        }
        exitCode = enriched && embedded ? 0 : 1;
        banner(exitCode == 0 ? "Enrichment and embedding queues drained"
                : "Stopped: a pass enriched or embedded nothing");
    }

    private static void banner(String message) {
        System.out.println();
        System.out.println("============================================");
        System.out.println("  " + message);
        System.out.println("  (see logs/enrichment.log for the detail)");
        System.out.println("============================================");
        System.out.println();
    }

    /** 1 when a pass attempted postings and enriched or embedded none of them. */
    @Override
    public int getExitCode() {
        return exitCode;
    }
}
