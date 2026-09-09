package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.EmbeddingStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The only place a job embedding is written.
 *
 * <p>It used to be a private method on the ingest path, which meant every posting the enrichment
 * sweep rescued was enriched and never embedded — complete-looking in the database and absent
 * from the recommendation feed, semantic search, similar jobs and the market corpus. Embedding is
 * now driven off its own state column, so a posting without a vector is a countable backlog item
 * rather than a silent gap.
 */
@Service
public class JobEmbeddingService implements EmbedJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(JobEmbeddingService.class);

    /** Courtesy gap between provider calls. */
    private static final long DELAY_MS = 200;

    private final JobRepositoryPort jobRepo;
    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final EmbeddingProviderPort embeddingProvider;
    private final int maxAttempts;
    private final Duration retryDelay;

    public JobEmbeddingService(JobRepositoryPort jobRepo,
                               JobEmbeddingRepositoryPort embeddingRepo,
                               @Qualifier("enrichmentAiProvider") EmbeddingProviderPort embeddingProvider,
                               @Value("${app.enrichment.max-attempts:4}") int maxAttempts,
                               @Value("${app.enrichment.retry-delay:PT6H}") Duration retryDelay) {
        this.jobRepo = jobRepo;
        this.embeddingRepo = embeddingRepo;
        this.embeddingProvider = embeddingProvider;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    @Override
    public boolean embed(Job job) {
        String text = textFor(job);
        if (text.isBlank()) return false;
        float[] vector = embeddingProvider.embed(text);
        if (vector == null || vector.length == 0) return false;
        embeddingRepo.save(new JobEmbedding(null, job.id(), vector,
                embeddingProvider.embeddingModelName(), Instant.now()));
        return true;
    }

    @Override
    public EmbedResult embedPending(int limit, Duration budget) {
        Instant deadline = Instant.now().plus(budget);
        List<Job> queue = jobRepo.findForEmbedding(limit, maxAttempts,
                Instant.now().minus(retryDelay));
        int total = queue.size();
        if (total == 0) return new EmbedResult(0, 0, 0, 0);

        long pending = jobRepo.countByEmbeddingStatus()
                .getOrDefault(EmbeddingStatus.PENDING, 0L);
        log.info("Embedding: starting batch of {} ({} pending overall)", total, pending);

        int succeeded = 0;
        int failed = 0;
        int gaveUp = 0;
        for (int i = 0; i < total; i++) {
            if (Instant.now().isAfter(deadline)) {
                log.info("Embedding: time budget spent after {} of {}", i, total);
                break;
            }
            Job job = queue.get(i);
            try {
                if (embed(job)) {
                    jobRepo.markEmbedded(job.id());
                    succeeded++;
                } else {
                    if (jobRepo.markEmbeddingFailed(job.id(), "no vector returned", maxAttempts)) gaveUp++;
                    failed++;
                }
            } catch (Exception e) {
                log.warn("Embedding failed for job {} ({}): {}", job.id(), job.title(), e.getMessage());
                if (jobRepo.markEmbeddingFailed(job.id(), e.getMessage(), maxAttempts)) gaveUp++;
                failed++;
            }
            if (i < total - 1) {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Map<EmbeddingStatus, Long> backlog = jobRepo.countByEmbeddingStatus();
        log.info("Embedding batch done: {} attempted, {} embedded, {} failed, {} given up on. "
                 + "Backlog now {}", total, succeeded, failed, gaveUp, backlog);
        return new EmbedResult(total, succeeded, failed, gaveUp);
    }

    /** Title plus cleaned description — the same text the old ingest-path embedding used. */
    private static String textFor(Job job) {
        return (job.title() != null ? job.title() : "") + " "
                + (job.descriptionClean() != null ? job.descriptionClean() : "");
    }
}
