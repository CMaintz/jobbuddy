package com.autoapplicant.adapter.cli;

import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enriches jobs that were saved without AI enrichment (ai_summary IS NULL).
 * Runs as a one-shot CLI command and exits.
 *
 * Usage (from repo root):
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=enrich"
 *
 * Optional: limit number of jobs processed per run (default: 500)
 *   ./gradlew :backend:bootRun --args="--spring.profiles.active=enrich --enrich.limit=100"
 *
 * Rate limiting: Gemini free tier allows 30 req/min. This runner sleeps 2 seconds
 * between enrichment calls (= 30/min) to stay within the limit.
 */
@Component
@Profile("enrich")
@Order(2)
public class EnrichmentSweepRunner implements ApplicationRunner {

    private static final int DEFAULT_LIMIT = 500;

    private final SweepUnenrichedJobsUseCase sweepUseCase;

    public EnrichmentSweepRunner(SweepUnenrichedJobsUseCase sweepUseCase) {
        this.sweepUseCase = sweepUseCase;
    }

    @Override
    public void run(ApplicationArguments args) {
        int limit = args.containsOption("enrich.limit")
                ? Integer.parseInt(args.getOptionValues("enrich.limit").get(0))
                : DEFAULT_LIMIT;

        SweepUnenrichedJobsUseCase.SweepResult result = sweepUseCase.sweep(limit);

        System.out.println();
        if (result.total() == 0) {
            System.out.println("============================================");
            System.out.println("  No unenriched jobs found. All done!");
            System.out.println("============================================");
        } else {
            System.out.println("============================================");
            System.out.printf("  Enrichment sweep complete%n");
            System.out.printf("  Processed : %d / %d%n", result.total(), result.total());
            System.out.printf("  Succeeded : %d%n", result.succeeded());
            System.out.printf("  Failed    : %d%n", result.failed());
            if (result.total() == limit) {
                System.out.println("  NOTE: limit reached — run again for more.");
            }
            System.out.println("============================================");
        }
        System.out.println();
    }
}
