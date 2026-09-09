package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

import java.time.Duration;

/**
 * Turning enriched postings into search vectors.
 *
 * <p>Separate from enrichment on purpose. They use different models with different constraints —
 * extraction can run on a local CLI agent, embedding cannot — and an embedding-model upgrade must
 * be able to re-embed the corpus without re-running extraction on it.
 */
public interface EmbedJobsUseCase {

    /** Embeds one posting. Returns false when the provider refused; the caller records that. */
    boolean embed(Job job);

    /** Embeds up to {@code limit} pending postings, stopping when {@code budget} is spent. */
    EmbedResult embedPending(int limit, Duration budget);

    record EmbedResult(int total, int succeeded, int failed, int gaveUp) {}
}
