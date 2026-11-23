package com.autoapplicant.usecase.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Many postings per CLI call, checked one by one: a call costs 15-25 s of her subscription
 * whatever it holds, so the saving is in the call count, and one bad item must not cost the
 * rest of the batch.
 */
class BatchedEnrichmentTest {

    private static final int MAX_ATTEMPTS = 4;
    private static final Pattern POSTING_ID = Pattern.compile("(?m)^=== POSTING (P\\d+) ===$");

    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final ObjectMapper json = new ObjectMapper();
    private final List<Job> jobs = List.of(job("Backend developer"), job("Data analyst"), job("Designer"));

    private static Job job(String title) {
        return Job.builder().id(UUID.randomUUID()).title(title).descriptionClean("We build things").build();
    }

    /** Plays the model: answers each call by turning the posting ids it was given into items. */
    private static final class FakeModel implements ChatProviderPort {
        final List<List<String>> calls = new ArrayList<>();
        Function<List<String>, String> answer = FakeModel::allValid;

        @Override
        public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
            List<String> ids = new ArrayList<>();
            Matcher m = POSTING_ID.matcher(composition.resolvedFinalPrompt());
            while (m.find()) {
                ids.add(m.group(1));
            }
            calls.add(ids);
            return AiCompletion.untracked(answer.apply(ids), "fake");
        }

        @Override
        public String chatModelName() {
            return "fake";
        }

        static String item(String id, String summary) {
            return "{\"id\":\"" + id + "\",\"aiSummary\":\"" + summary + "\",\"boilerplateLines\":[]}";
        }

        static String allValid(List<String> ids) {
            return answer(ids.stream().map(id -> item(id, "summary of " + id)).toList());
        }

        static String answer(List<String> items) {
            return "{\"postings\":[" + String.join(",", items) + "]}";
        }
    }

    private final FakeModel model = new FakeModel();

    private BatchedEnrichment.Result run(int batchSize) {
        BatchedEnrichment batched = new BatchedEnrichment(model, json,
                new JobEnrichmentService(model, json), jobRepo);
        return batched.enrich(jobs, batchSize, MAX_ATTEMPTS);
    }

    @Test
    void oneCallEnrichesAWholeBatch() {
        // Bug: the batch is sent one posting per call, so batching saves nothing.
        BatchedEnrichment.Result result = run(3);

        assertThat(model.calls).hasSize(1);
        assertThat(result.succeeded()).isEqualTo(3);
        verify(jobRepo, times(3)).saveEnrichment(argThat(j -> j.aiSummary() != null));
        verify(jobRepo, times(3)).markEnriched(any());
    }

    @Test
    void aPostingMissingFromTheAnswerIsRetriedAndTheOthersAreKept() {
        // Bug: one item the model skipped sinks the whole batch, and all three are tried again.
        model.answer = ids -> ids.size() > 1
                ? FakeModel.answer(List.of(FakeModel.item("P1", "a"), FakeModel.item("P3", "c")))
                : FakeModel.allValid(ids);

        BatchedEnrichment.Result result = run(3);

        assertThat(result.succeeded()).isEqualTo(3);
        assertThat(model.calls).hasSize(2);
        assertThat(model.calls.get(1)).hasSize(1);
        verify(jobRepo, never()).markEnrichmentFailed(any(), anyString(), anyInt());
    }

    @Test
    void itemsAreMatchedByIdNeverByPosition() {
        // Bug: answers are matched by position, so a model that reorders them gives each
        // posting another posting's summary; or a duplicated or invented id is trusted.
        model.answer = ids -> ids.size() > 1
                ? FakeModel.answer(List.of(FakeModel.item("P9", "invented"),
                        FakeModel.item("P2", "first"), FakeModel.item("P2", "second"),
                        FakeModel.item("P1", "summary of P1")))
                : FakeModel.allValid(ids);

        run(3);

        verify(jobRepo).saveEnrichment(argThat(j -> j.id().equals(jobs.get(0).id())
                && "summary of P1".equals(j.aiSummary())));
        verify(jobRepo, never()).saveEnrichment(argThat(j -> "invented".equals(j.aiSummary())
                || "first".equals(j.aiSummary()) || "second".equals(j.aiSummary())));
        verify(jobRepo, times(3)).markEnriched(any());
    }

    @Test
    void aPostingThatFailsEvenAloneCostsOneAttempt() {
        // Bug: the attempt is counted at every retry level, so a posting is given up on after
        // two sweeps instead of four.
        UUID stubborn = jobs.get(1).id();
        model.answer = ids -> FakeModel.answer(ids.stream()
                .filter(id -> !(ids.size() == 1 || id.equals("P2")))
                .map(id -> FakeModel.item(id, "s")).toList());

        BatchedEnrichment.Result result = run(3);

        assertThat(result.failed()).isEqualTo(1);
        verify(jobRepo, times(1)).markEnrichmentFailed(eq(stubborn), anyString(), eq(MAX_ATTEMPTS));
    }

    @Test
    void aProviderErrorStopsTheRunWithoutFanningOutIntoSingleCalls() {
        // Bug: a usage-limit or timeout error is treated as bad items, and the batch is retried
        // as one call per posting, each failing the same way and each costing an attempt.
        model.answer = ids -> {
            throw new IllegalStateException("usage limit reached");
        };

        BatchedEnrichment.Result result = run(3);

        assertThat(result.stopped()).isTrue();
        assertThat(model.calls).hasSize(1);
        verify(jobRepo, never()).markEnrichmentFailed(any(), anyString(), anyInt());
        verify(jobRepo, never()).saveEnrichment(any());
    }

    @Test
    void largerQueuesAreSplitIntoBatchesOfTheConfiguredSize() {
        // Bug: the whole queue goes into one prompt, past what the CLI can answer in time.
        BatchedEnrichment.Result result = run(2);

        assertThat(model.calls).extracting(List::size).containsExactly(2, 1);
        assertThat(result.succeeded()).isEqualTo(3);
    }

    @Test
    void aSweepWithABatchSizeSendsItsQueueInBatches() {
        // Bug: the batch size is configured but the sweep still makes one call per posting.
        org.mockito.Mockito.when(jobRepo.findForEnrichment(anyInt(), anyInt(), any(), any())).thenReturn(jobs);
        EnrichmentSweepService sweep = new EnrichmentSweepService(jobRepo,
                new JobEnrichmentService(model, json),
                new BatchedEnrichment(model, json, new JobEnrichmentService(model, json), jobRepo),
                mock(com.autoapplicant.port.out.user.PreferencesRepositoryPort.class),
                new EnrichmentSweepSettings(MAX_ATTEMPTS, java.time.Duration.ofHours(6),
                        java.time.Duration.ofMinutes(5), 0, 2, 3));

        sweep.sweep(10);

        assertThat(model.calls).hasSize(1);
        verify(jobRepo, times(3)).markEnriched(any());
    }

    @Test
    void anUnreadableAnswerToABatchIsRetriedLikeMissingItems() {
        // Bug: an answer that does not parse stops the run as if the provider had failed. The
        // queue order is stable, so every sweep sends the same head batch, gets the same answer
        // and stops again: enrichment stalls for good and spends a call per sweep.
        model.answer = ids -> ids.size() > 1 ? "Here are the postings you asked about." : FakeModel.allValid(ids);

        BatchedEnrichment.Result result = run(2);

        assertThat(result.stopped()).isFalse();
        assertThat(result.succeeded()).isEqualTo(3);
    }

    @Test
    void aHeadBatchThatNeverParsesStillMovesTheQueueOn() {
        // Bug: the same unparseable head batch is never charged, so its postings are sent first
        // on every sweep and nothing behind them is ever reached.
        org.mockito.Mockito.when(jobRepo.findForEnrichment(anyInt(), anyInt(), any(), any())).thenReturn(jobs);
        model.answer = ids -> "not json at all";
        EnrichmentSweepService sweep = new EnrichmentSweepService(jobRepo,
                new JobEnrichmentService(model, json),
                new BatchedEnrichment(model, json, new JobEnrichmentService(model, json), jobRepo),
                mock(com.autoapplicant.port.out.user.PreferencesRepositoryPort.class),
                new EnrichmentSweepSettings(MAX_ATTEMPTS, java.time.Duration.ofHours(6),
                        java.time.Duration.ofMinutes(5), 0, 2, 3));

        sweep.sweep(10);

        for (Job job : jobs) {
            verify(jobRepo, times(1)).markEnrichmentFailed(eq(job.id()), anyString(), eq(MAX_ATTEMPTS));
        }
    }

    @Test
    void aPostingLeftAloneInAHalfSizeBatchIsNotSentAloneAgain() {
        // Bug: a half-size chunk of one is tried, fails, and is then sent on its own a second
        // time, paying twice for a posting the model cannot answer.
        model.answer = ids -> FakeModel.answer(List.of());

        run(4);

        assertThat(model.calls).extracting(List::size).containsExactly(3, 2, 1, 1, 1);
        verify(jobRepo, times(3)).markEnrichmentFailed(any(), anyString(), eq(MAX_ATTEMPTS));
    }
}
