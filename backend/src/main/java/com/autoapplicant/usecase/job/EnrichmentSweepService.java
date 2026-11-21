package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.EnrichmentScope;
import com.autoapplicant.domain.job.EnrichmentStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drains the enrichment queue. The queue is the {@code enrichment_status} column, not an
 * in-memory one: every posting the crawler saves starts PENDING and stays that way until this
 * runs, so nothing is ever dropped for want of space and the backlog can be counted at any time.
 *
 * <p>Outcomes are written to the job. Without that a posting the model can never summarise comes
 * back on every pass forever, spending quota each time.
 */
@Service
public class EnrichmentSweepService implements SweepUnenrichedJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSweepService.class);

    /**
     * Courtesy gap between windows. Small on purpose: this is the only enrichment path now, not
     * a recovery pass, so a two-second sleep per posting was two seconds of pure tax on every
     * one of thousands.
     */
    private final long delayMs;

    /**
     * Postings in flight at once. The old ingest path ran two concurrently; going strictly
     * one-at-a-time here would have made the primary path slower than the thing it replaced.
     */
    private final int concurrency;

    /** Progress is reported on a clock, not per job, so a slow batch still shows movement. */
    private static final Duration PROGRESS_EVERY = Duration.ofSeconds(30);

    private final JobRepositoryPort jobRepo;
    private final JobEnrichmentService enrichJob;
    private final PreferencesRepositoryPort prefsRepo;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration callTimeout;

    public EnrichmentSweepService(JobRepositoryPort jobRepo,
                                  JobEnrichmentService enrichJob,
                                  PreferencesRepositoryPort prefsRepo,
                                  EnrichmentSweepSettings settings) {
        this.jobRepo = jobRepo;
        this.enrichJob = enrichJob;
        this.prefsRepo = prefsRepo;
        this.maxAttempts = settings.maxAttempts();
        this.retryDelay = settings.retryDelay();
        this.callTimeout = settings.callTimeout();
        this.delayMs = settings.delayMs();
        this.concurrency = settings.concurrency();
    }

    @Override
    public SweepResult sweep(int limit) {
        return sweep(limit, Duration.ofDays(365));
    }

    /**
     * Enriches up to {@code limit} postings, stopping early when {@code budget} is spent.
     *
     * <p>Bounded by time as well as count because the caller is a scheduled worker: a batch that
     * runs long should hand the thread back rather than hold it until the next tick piles up
     * behind it.
     */
    @Override
    public SweepResult sweep(int limit, Duration budget) {
        Instant deadline = Instant.now().plus(budget);
        Instant retryBefore = Instant.now().minus(retryDelay);
        List<Job> queue = jobRepo.findForEnrichment(limit, maxAttempts, retryBefore,
                EnrichmentScope.of(prefsRepo.findAll()));
        int total = queue.size();
        if (total == 0) return new SweepResult(0, 0, 0, 0);

        // The denominator: how much is left overall, not just in this batch. "10,100 enriched"
        // says nothing without it.
        long pending = pendingCount();
        Instant startedAt = Instant.now();
        Instant lastProgressAt = startedAt;
        int succeeded = 0;
        int failed = 0;
        int gaveUp = 0;

        log.info("Enrichment: starting batch of {} ({} pending overall)", total, pending);

        // Windowed: `concurrency` postings are submitted together and awaited together, so the
        // provider is kept busy without an unbounded number of calls in flight.
        int done = 0;
        outer:
        for (int start = 0; start < total; start += concurrency) {
            if (Instant.now().isAfter(deadline)) {
                log.info("Enrichment: time budget spent after {} of {} — handing back, "
                         + "the next run continues where this stopped", done, total);
                break;
            }
            List<Job> window = queue.subList(start, Math.min(start + concurrency, total));
            List<Map.Entry<Job, java.util.concurrent.CompletableFuture<Job>>> inFlight =
                    window.stream().map(j -> Map.entry(j, enrichJob.enrich(j))).toList();

            for (Map.Entry<Job, java.util.concurrent.CompletableFuture<Job>> entry : inFlight) {
                Job job = entry.getKey();
                try {
                    // Bounded: an unbounded get() on a pool that can reject leaves a future
                    // nobody will ever complete, and the thread waits on it forever.
                    Job enriched = entry.getValue().get(callTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    if (enriched.aiSummary() != null) {
                        jobRepo.saveEnrichment(enriched);
                        jobRepo.markEnriched(job.id());
                        succeeded++;
                    } else {
                        // Not an exception, but not a result either — count it as an attempt
                        // so it cannot loop forever.
                        if (jobRepo.markEnrichmentFailed(job.id(), "no summary returned", maxAttempts)) gaveUp++;
                        failed++;
                    }
                } catch (TimeoutException te) {
                    // The provider or the pool is not keeping up. That is not this posting's
                    // fault, so it keeps its attempts and stays PENDING; stopping is honest.
                    log.warn("Enrichment: timed out after {} on job {} — provider or pool is "
                             + "saturated, ending this batch with {} of {} done",
                            callTimeout, job.id(), done, total);
                    break outer;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.info("Enrichment: interrupted after {} of {}", done, total);
                    break outer;
                } catch (Exception e) {
                    log.warn("Enrichment failed for job {} ({}): {}", job.id(), job.title(), e.getMessage());
                    if (jobRepo.markEnrichmentFailed(job.id(), e.getMessage(), maxAttempts)) gaveUp++;
                    failed++;
                }
                done++;
            }

            if (Duration.between(lastProgressAt, Instant.now()).compareTo(PROGRESS_EVERY) >= 0) {
                logProgress(succeeded, failed, done, total, pending, startedAt);
                lastProgressAt = Instant.now();
            }

            if (delayMs > 0 && start + concurrency < total) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        SweepResult result = new SweepResult(total, succeeded, failed, gaveUp);
        logProgress(succeeded, failed, succeeded + failed, total, pending, startedAt);
        return result;
    }

    /** Postings still waiting — the number that makes a progress line mean something. */
    private long pendingCount() {
        Map<EnrichmentStatus, Long> counts = jobRepo.countByEnrichmentStatus();
        return counts.getOrDefault(EnrichmentStatus.PENDING, 0L);
    }

    /**
     * "Enrichment: 41/60 in this batch · 7,183 pending · 612/h · ~11h44m left".
     * The rate is measured, not assumed, so the estimate tracks whatever the provider is
     * actually managing today.
     */
    private void logProgress(int succeeded, int failed, int done, int total, long pendingAtStart,
                             Instant startedAt) {
        if (done <= 0) return;
        Duration elapsed = Duration.between(startedAt, Instant.now());
        double perHour = elapsed.toMillis() > 0
                ? done / (elapsed.toMillis() / 3_600_000.0) : 0;
        long remaining = Math.max(0, pendingAtStart - succeeded);
        String eta = perHour > 0 ? humanise(Duration.ofSeconds((long) (remaining / perHour * 3600))) : "unknown";
        log.info("Enrichment: {}/{} in this batch ({} ok, {} failed) · {} pending overall · "
                 + "{}/h · ~{} left",
                done, total, succeeded, failed, remaining, Math.round(perHour), eta);
    }

    private static String humanise(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours > 0 ? hours + "h" + minutes + "m" : minutes + "m";
    }
}
