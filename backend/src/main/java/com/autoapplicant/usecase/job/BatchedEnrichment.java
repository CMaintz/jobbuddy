package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Enriches postings many per call, for a chat provider where the call, not the token, is the
 * cost: a {@code claude -p} run spends 15-25 s of a subscription whatever it holds.
 *
 * <p>One malformed item costs only itself ({@link BatchAnswer}). Postings that come back missing
 * or invalid are tried again in half-size batches, then one at a time; an attempt is recorded
 * only when a posting fails on its own, so an answer that never parses still moves each posting
 * towards being given up on rather than stalling the queue. A provider error (a non-zero exit,
 * no output, a timeout) stops the run: the next posting would fail the same way, and retrying
 * one by one would turn one failed call into dozens.
 */
@Component
public class BatchedEnrichment {

    private static final Logger log = LoggerFactory.getLogger(BatchedEnrichment.class);
    private static final String SYSTEM = "You are a job data enrichment assistant. Respond only with JSON.";
    private static final String OPERATION = "job-enrichment-batch";
    private static final long NANOS_PER_MILLI = 1_000_000;

    /**
     * @param stopped true when a provider error ended the run early; the postings not yet
     *     enriched keep their attempts and stay queued
     */
    public record Result(int total, int succeeded, int failed, int gaveUp, boolean stopped) {}

    private final ChatProviderPort provider;
    private final BatchAnswer answers;
    private final JobRepositoryPort jobRepo;

    public BatchedEnrichment(@Qualifier("enrichmentBatchChatProvider") ChatProviderPort provider,
                             ObjectMapper json, JobEnrichmentService parser, JobRepositoryPort jobRepo) {
        this.provider = provider;
        this.answers = new BatchAnswer(json, parser);
        this.jobRepo = jobRepo;
    }

    /** Enriches {@code jobs} in batches of {@code batchSize}, saving each posting as it succeeds. */
    public Result enrich(List<Job> jobs, int batchSize, int maxAttempts) {
        Tally tally = new Tally();
        try {
            for (List<Job> batch : chunks(jobs, batchSize)) {
                List<Job> failedAlone = enrichWithRetries(batch, batchSize, tally);
                recordFailures(failedAlone, maxAttempts, tally);
            }
        } catch (RuntimeException providerError) {
            log.warn("Enrichment: provider failed ({}); stopping this run after {} postings",
                    providerError.getMessage(), tally.succeeded);
            return tally.result(jobs.size(), true);
        }
        return tally.result(jobs.size(), false);
    }

    /**
     * The batch, then what it missed in half-size batches, then what is left one by one. A
     * posting is never sent alone twice. Returns the postings that failed on their own.
     */
    private List<Job> enrichWithRetries(List<Job> batch, int batchSize, Tally tally) {
        if (batch.size() == 1) {
            return attempt(batch, tally);
        }
        List<Job> missed = attempt(batch, tally);
        List<Job> failedAlone = new ArrayList<>();
        int half = batchSize / 2;
        if (missed.size() > 1 && half > 1) {
            missed = retryInHalves(missed, half, failedAlone, tally);
        }
        for (Job single : missed) {
            failedAlone.addAll(attempt(List.of(single), tally));
        }
        return failedAlone;
    }

    /**
     * Retries {@code missed} in batches of {@code half}; a posting that was alone in its batch
     * goes straight to {@code failedAlone}. Returns the rest, still to be tried alone.
     */
    private List<Job> retryInHalves(List<Job> missed, int half, List<Job> failedAlone, Tally tally) {
        List<Job> stillMissed = new ArrayList<>();
        for (List<Job> smaller : chunks(missed, half)) {
            (smaller.size() == 1 ? failedAlone : stillMissed).addAll(attempt(smaller, tally));
        }
        return stillMissed;
    }

    /** One call; saves what came back valid and returns the postings that did not. */
    private List<Job> attempt(List<Job> batch, Tally tally) {
        Map<String, Job> byId = new LinkedHashMap<>();
        for (int i = 0; i < batch.size(); i++) {
            byId.put("P" + (i + 1), batch.get(i));
        }
        long started = System.nanoTime();
        Map<String, Job> enriched = answers.validItems(call(byId), byId);
        log.info("Enrichment batch: {} postings, {} valid, {} ms", byId.size(), enriched.size(),
                (System.nanoTime() - started) / NANOS_PER_MILLI);
        List<Job> missed = new ArrayList<>();
        byId.forEach((id, job) -> {
            Job done = enriched.get(id);
            if (done == null) {
                missed.add(job);
            } else {
                jobRepo.saveEnrichment(done);
                jobRepo.markEnriched(job.id());
                tally.succeeded++;
            }
        });
        return missed;
    }

    private String call(Map<String, Job> byId) {
        String prompt = EnrichmentPrompt.batch(List.copyOf(byId.keySet()), List.copyOf(byId.values()));
        return provider.generateJson(
                new PromptComposition(SYSTEM, prompt, "", "", "", "", prompt), OPERATION);
    }

    private void recordFailures(List<Job> failedAlone, int maxAttempts, Tally tally) {
        for (Job failed : failedAlone) {
            tally.failed++;
            if (jobRepo.markEnrichmentFailed(failed.id(),
                    "invalid or missing in the enrichment answer", maxAttempts)) {
                tally.gaveUp++;
            }
        }
    }

    private static List<List<Job>> chunks(List<Job> jobs, int size) {
        List<List<Job>> chunks = new ArrayList<>();
        for (int start = 0; start < jobs.size(); start += size) {
            chunks.add(jobs.subList(start, Math.min(start + size, jobs.size())));
        }
        return chunks;
    }

    private static final class Tally {
        private int succeeded;
        private int failed;
        private int gaveUp;

        Result result(int total, boolean stopped) {
            return new Result(total, succeeded, failed, gaveUp, stopped);
        }
    }
}
