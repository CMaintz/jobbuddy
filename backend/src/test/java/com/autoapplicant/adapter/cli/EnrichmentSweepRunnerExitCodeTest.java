package com.autoapplicant.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.in.job.EmbedJobsUseCase.EmbedResult;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase.SweepResult;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * A scheduled or scripted {@code enrich} run only learns that the provider broke from the exit
 * code. The sweep catches per-posting failures so one bad posting cannot sink the batch, which
 * is why the code has to come from what the passes did and not from whether {@code run} threw.
 */
class EnrichmentSweepRunnerExitCodeTest {

    private static final SweepResult EMPTY = new SweepResult(0, 0, 0, 0);

    private final SweepUnenrichedJobsUseCase enrichment = Mockito.mock(SweepUnenrichedJobsUseCase.class);
    private final EmbedJobsUseCase embedding = Mockito.mock(EmbedJobsUseCase.class);

    EnrichmentSweepRunnerExitCodeTest() {
        when(enrichment.sweep(anyInt())).thenReturn(EMPTY);
        when(embedding.embedPending(anyInt(), any(Duration.class))).thenReturn(new EmbedResult(0, 0, 0, 0));
    }

    private int exitCodeAfterRunning() {
        EnrichmentSweepRunner runner = new EnrichmentSweepRunner(new QueueDrainer(enrichment, embedding));
        runner.run(new DefaultApplicationArguments());
        return runner.getExitCode();
    }

    @Test
    void aPassWhereTheProviderFailedEveryPostingExitsNonZero() {
        // Bug: a missing login or a provider outage fails every posting, yet the run exits 0
        // and the scheduled job shows green while nothing gets enriched.
        when(enrichment.sweep(anyInt())).thenReturn(new SweepResult(5, 0, 5, 0));

        assertThat(exitCodeAfterRunning()).isEqualTo(1);
    }

    @Test
    void aPassThatOnlyGaveUpOnPostingsExitsNonZero() {
        // Bug: giving up counts as progress, so the run that retires the postings for good is
        // the one that shows green.
        when(enrichment.sweep(anyInt())).thenReturn(new SweepResult(5, 0, 5, 5)).thenReturn(EMPTY);

        assertThat(exitCodeAfterRunning()).isEqualTo(1);
    }

    @Test
    void aRunThatEnrichedPostingsExitsZero() {
        // Bug: a flipped or always-on failure check turns every run red, and a red that is
        // always on gets ignored.
        when(enrichment.sweep(anyInt())).thenReturn(new SweepResult(5, 4, 1, 0)).thenReturn(EMPTY);

        assertThat(exitCodeAfterRunning()).isZero();
    }

    @Test
    void aRunWithNothingToDoExitsZero() {
        // Bug: "enriched none" read without "attempted some" makes an empty queue a failure.
        assertThat(exitCodeAfterRunning()).isZero();
    }

    @Test
    void aRunWhereNoPostingCouldBeEmbeddedExitsNonZero() {
        // Bug: the runner drains embedding but drops its verdict, so a revoked embedding key
        // stays green until every posting is given up on.
        when(embedding.embedPending(anyInt(), any(Duration.class))).thenReturn(new EmbedResult(3, 0, 3, 0));

        assertThat(exitCodeAfterRunning()).isEqualTo(1);
    }
}
