package com.autoapplicant.usecase.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase.SweepResult;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnrichmentSweepServiceTest {

    private static final int MAX_ATTEMPTS = 4;
    private static final UUID JOB_ID = UUID.randomUUID();

    private JobRepositoryPort jobRepo;
    private JobEnrichmentService enrichJob;
    private EnrichmentSweepService service;

    @BeforeEach
    void setUp() {
        jobRepo = mock(JobRepositoryPort.class);
        enrichJob = mock(JobEnrichmentService.class);
        // No delay and no concurrency in tests: the behaviour under test is the bookkeeping,
        // not the pacing.
        service = new EnrichmentSweepService(jobRepo, enrichJob, MAX_ATTEMPTS, Duration.ofHours(6),
                Duration.ofMinutes(5), 0L, 1);
    }

    private static Job job(String summary) {
        return job(JOB_ID, summary);
    }

    private static Job job(UUID id, String summary) {
        return Job.builder().id(id).title("Platform Engineer").aiSummary(summary).build();
    }

    /** A provider call that never answers, as when the pool is saturated or the call hangs. */
    private static CompletableFuture<Job> hung() {
        return new CompletableFuture<>();
    }

    /** Short enough that a hung call ends the test quickly instead of after the real 5 minutes. */
    private EnrichmentSweepService serviceWith(int concurrency) {
        return new EnrichmentSweepService(jobRepo, enrichJob, MAX_ATTEMPTS, Duration.ofHours(6),
                Duration.ofMillis(50), 0L, concurrency);
    }

    private void queue(Job... jobs) {
        when(jobRepo.findForEnrichment(anyInt(), eq(MAX_ATTEMPTS), any())).thenReturn(List.of(jobs));
    }

    @Test
    void a_successful_pass_marks_the_job_done_so_it_is_not_asked_about_again() {
        queue(job(null));
        when(enrichJob.enrich(any())).thenReturn(CompletableFuture.completedFuture(job("a summary")));

        SweepResult result = service.sweep(10);

        assertThat(result).isEqualTo(new SweepResult(1, 1, 0, 0));
        verify(jobRepo).markEnriched(JOB_ID);
        verify(jobRepo, never()).markEnrichmentFailed(any(), any(), anyInt());
    }

    @Test
    void a_pass_that_returns_no_summary_counts_as_an_attempt_rather_than_looping_forever() {
        // This was the old bug: no exception, no summary, nothing recorded, so the job
        // came back on every sweep and spent quota each time.
        queue(job(null));
        when(enrichJob.enrich(any())).thenReturn(CompletableFuture.completedFuture(job(null)));

        SweepResult result = service.sweep(10);

        assertThat(result.failed()).isEqualTo(1);
        verify(jobRepo).markEnrichmentFailed(eq(JOB_ID), eq("no summary returned"), eq(MAX_ATTEMPTS));
        verify(jobRepo, never()).markEnriched(any());
    }

    @Test
    void a_thrown_failure_is_recorded_with_its_reason() {
        queue(job(null));
        when(enrichJob.enrich(any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("429 rate limited")));

        SweepResult result = service.sweep(10);

        assertThat(result.failed()).isEqualTo(1);
        verify(jobRepo).markEnrichmentFailed(eq(JOB_ID), contains("429"), eq(MAX_ATTEMPTS));
    }

    @Test
    void the_pass_that_hits_the_attempt_limit_is_reported_as_given_up_on() {
        queue(job(null));
        when(enrichJob.enrich(any())).thenReturn(CompletableFuture.completedFuture(job(null)));
        when(jobRepo.markEnrichmentFailed(any(), any(), anyInt())).thenReturn(true);

        assertThat(service.sweep(10).gaveUp()).isEqualTo(1);
    }

    @Test
    void an_empty_queue_does_nothing_at_all() {
        queue();

        assertThat(service.sweep(10)).isEqualTo(new SweepResult(0, 0, 0, 0));
        verifyNoInteractions(enrichJob);
    }

    @Test
    void the_queue_is_asked_for_jobs_not_retried_within_the_configured_delay() {
        queue();
        Instant before = Instant.now().minus(Duration.ofHours(6));

        service.sweep(25);

        verify(jobRepo).findForEnrichment(eq(25), eq(MAX_ATTEMPTS),
                argThat(cutoff -> !cutoff.isBefore(before.minusSeconds(5))
                        && !cutoff.isAfter(before.plusSeconds(5))));
    }

    @Test
    void a_timed_out_call_keeps_the_jobs_attempts_and_ends_the_batch() {
        // A timeout says the provider is not keeping up, not that the posting is bad. Charging
        // it an attempt would retire good postings during an outage, and carrying on would
        // just time out on every remaining one.
        Job first = job(UUID.randomUUID(), null);
        Job second = job(UUID.randomUUID(), null);
        queue(first, second);
        when(enrichJob.enrich(any())).thenReturn(hung());

        SweepResult result = serviceWith(1).sweep(10);

        assertThat(result).isEqualTo(new SweepResult(2, 0, 0, 0));
        verify(jobRepo, never()).markEnrichmentFailed(any(), any(), anyInt());
        verify(enrichJob, never()).enrich(second);
    }

    @Test
    void a_spent_time_budget_hands_back_before_starting_another_window() {
        // Already spent rather than zero: the deadline check is a strict isAfter, so a zero
        // budget passes or fails depending on whether the clock ticked in between.
        queue(job(null));

        SweepResult result = serviceWith(1).sweep(10, Duration.ofSeconds(-1));

        assertThat(result).isEqualTo(new SweepResult(1, 0, 0, 0));
        verifyNoInteractions(enrichJob);
    }

    @Test
    void a_window_puts_concurrency_postings_in_flight_and_no_more() {
        // The first call hangs, so whatever was submitted before the batch gave up shows how
        // much was in flight at once: one means no concurrency, three means no bound.
        Job first = job(UUID.randomUUID(), null);
        Job second = job(UUID.randomUUID(), null);
        Job third = job(UUID.randomUUID(), null);
        queue(first, second, third);
        when(enrichJob.enrich(first)).thenReturn(hung());
        when(enrichJob.enrich(second)).thenReturn(CompletableFuture.completedFuture(job("a summary")));

        serviceWith(2).sweep(10);

        verify(enrichJob).enrich(first);
        verify(enrichJob).enrich(second);
        verify(enrichJob, never()).enrich(third);
    }
}
