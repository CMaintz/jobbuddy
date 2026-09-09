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
    /** Texts per API call. The endpoint accepts an array; there is no reason to send one. */
    private final int chunkSize;

    public JobEmbeddingService(JobRepositoryPort jobRepo,
                               JobEmbeddingRepositoryPort embeddingRepo,
                               @Qualifier("enrichmentAiProvider") EmbeddingProviderPort embeddingProvider,
                               @Value("${app.enrichment.max-attempts:4}") int maxAttempts,
                               @Value("${app.enrichment.retry-delay:PT6H}") Duration retryDelay,
                               @Value("${app.embedding.sweep.chunk-size:100}") int chunkSize) {
        this.jobRepo = jobRepo;
        this.embeddingRepo = embeddingRepo;
        this.embeddingProvider = embeddingProvider;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
        this.chunkSize = Math.max(1, chunkSize);
    }

    @Override
    public boolean embed(Job job) {
        float[] vector = embedVector(job);
        if (vector == null || vector.length == 0) return false;
        embeddingRepo.save(new JobEmbedding(null, job.id(), vector,
                embeddingProvider.embeddingModelName(), Instant.now()));
        return true;
    }

    private float[] embedVector(Job job) {
        String text = textFor(job);
        return text.isBlank() ? null : embeddingProvider.embed(text);
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

        // Chunked rather than one-at-a-time: the embeddings endpoint takes an array and returns
        // vectors in order, so a chunk is one round trip instead of `chunkSize` of them. On a
        // backlog of thousands that is the difference between minutes and hours.
        for (int start = 0; start < total; start += chunkSize) {
            if (Instant.now().isAfter(deadline)) {
                log.info("Embedding: time budget spent after {} of {}", start, total);
                break;
            }
            List<Job> chunk = queue.subList(start, Math.min(start + chunkSize, total));
            List<String> texts = chunk.stream().map(JobEmbeddingService::textFor).toList();

            List<float[]> vectors;
            try {
                vectors = embeddingProvider.embedAll(texts);
            } catch (Exception e) {
                // One bad chunk should not sink the batch; fall back so a single unembeddable
                // posting is isolated rather than taking its neighbours down with it.
                log.warn("Embedding: batch of {} failed ({}) — retrying individually",
                        chunk.size(), e.getMessage());
                vectors = List.of();
            }

            for (int i = 0; i < chunk.size(); i++) {
                Job job = chunk.get(i);
                try {
                    float[] vector = i < vectors.size() ? vectors.get(i) : null;
                    if (vector == null || vector.length == 0) vector = embedVector(job);
                    if (vector != null && vector.length > 0) {
                        embeddingRepo.save(new JobEmbedding(null, job.id(), vector,
                                embeddingProvider.embeddingModelName(), Instant.now()));
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
            }

            if (start + chunkSize < total) {
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
