package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.domain.job.EnrichmentStatus;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase.SweepResult;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.job.EnrichmentSweepService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class EnrichmentSweepSchedulerTest {

    private final EnrichmentSweepService sweep = mock(EnrichmentSweepService.class);
    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final CrawlerStateRepositoryPort crawlerState = mock(CrawlerStateRepositoryPort.class);

    /** Runs the worker body inline so the test needs no waiting. */
    private final Executor inline = Runnable::run;

    private static final Duration BUDGET = Duration.ofMinutes(20);

    private EnrichmentSweepScheduler scheduler(boolean enabled) {
        return new EnrichmentSweepScheduler(sweep, jobRepo, crawlerState, inline, enabled, 200, BUDGET);
    }

    private static CrawlerState state(boolean running) {
        return new CrawlerState("TEAMTAILOR", 0, Instant.now(), null, 0, 0, null, running, Instant.now());
    }

    @Test
    void the_worker_drains_the_queue_without_being_asked() {
        when(crawlerState.findAll()).thenReturn(List.of(state(false)));
        when(sweep.sweep(200, BUDGET)).thenReturn(new SweepResult(3, 2, 1, 0));
        when(jobRepo.countByEnrichmentStatus()).thenReturn(Map.of(EnrichmentStatus.PENDING, 5L));

        scheduler(true).scheduledSweep();

        verify(sweep).sweep(200, BUDGET);
    }

    @Test
    void it_stands_down_while_a_crawl_is_running() {
        // Ingest is cheap and enrichment is not. Letting the crawl finish gets the postings in
        // faster, and stops the two competing for the same provider quota.
        when(crawlerState.findAll()).thenReturn(List.of(state(false), state(true)));

        scheduler(true).scheduledSweep();

        verify(sweep, never()).sweep(anyInt(), any());
    }

    @Test
    void a_tick_arriving_mid_batch_is_skipped_rather_than_queued() {
        when(crawlerState.findAll()).thenReturn(List.of(state(false)));
        EnrichmentSweepScheduler scheduler = scheduler(true);
        // Re-entering from inside the batch is what a slow provider plus a fast tick looks like.
        when(sweep.sweep(anyInt(), any())).thenAnswer(inv -> {
            scheduler.scheduledSweep();
            return new SweepResult(1, 1, 0, 0);
        });
        when(jobRepo.countByEnrichmentStatus()).thenReturn(Map.of());

        scheduler.scheduledSweep();

        // Once, not twice: the second tick found the first still working.
        verify(sweep, times(1)).sweep(anyInt(), any());
    }

    @Test
    void a_deployment_can_turn_it_off() {
        scheduler(false).scheduledSweep();

        verifyNoInteractions(sweep);
        verifyNoInteractions(crawlerState);
    }

    @Test
    void an_empty_backlog_is_not_worth_a_log_line() {
        when(crawlerState.findAll()).thenReturn(List.of());
        when(sweep.sweep(anyInt(), any())).thenReturn(new SweepResult(0, 0, 0, 0));

        scheduler(true).scheduledSweep();

        verify(sweep).sweep(200, BUDGET);
        verify(jobRepo, never()).countByEnrichmentStatus();
    }
}
