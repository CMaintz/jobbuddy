package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.EnrichmentStatus;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase.SweepResult;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class EnrichmentSweepSchedulerTest {

    private final SweepUnenrichedJobsUseCase sweep = mock(SweepUnenrichedJobsUseCase.class);
    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);

    private EnrichmentSweepScheduler scheduler(boolean enabled) {
        return new EnrichmentSweepScheduler(sweep, jobRepo, enabled, 40);
    }

    @Test
    void the_sweep_runs_on_its_own_now_rather_than_waiting_for_someone_to_type_a_command() {
        when(sweep.sweep(40)).thenReturn(new SweepResult(3, 2, 1, 0));
        when(jobRepo.countByEnrichmentStatus()).thenReturn(Map.of(EnrichmentStatus.PENDING, 5L));

        scheduler(true).scheduledSweep();

        verify(sweep).sweep(40);
    }

    @Test
    void a_deployment_can_turn_it_off() {
        scheduler(false).scheduledSweep();

        verifyNoInteractions(sweep);
    }

    @Test
    void an_empty_backlog_is_not_worth_a_log_line_every_hour() {
        when(sweep.sweep(anyInt())).thenReturn(new SweepResult(0, 0, 0, 0));

        scheduler(true).scheduledSweep();

        verify(sweep).sweep(40);
        verifyNoInteractions(jobRepo);
    }
}
