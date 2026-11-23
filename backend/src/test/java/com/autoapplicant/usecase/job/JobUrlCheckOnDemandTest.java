package com.autoapplicant.usecase.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.UrlProbeOutcome;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.JobUrlProbePort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The crawl run asks for a probe explicitly; the enabled switch is for the in-process schedule.
 * The scheduled crawl turns that schedule off, so an explicit request must not depend on it.
 */
class JobUrlCheckOnDemandTest {

    private static final String URL = "https://jobs.example.dk/123";

    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final JobUrlProbePort urlProbe = mock(JobUrlProbePort.class);
    private final UUID jobId = UUID.randomUUID();

    JobUrlCheckOnDemandTest() {
        Job job = Job.builder().id(jobId).url(URL).title("Developer").build();
        when(jobRepo.findUrlCheckCandidates(any(), anyInt())).thenReturn(List.of(job));
        when(jobRepo.countActive()).thenReturn(1L);
        when(urlProbe.probe(URL)).thenReturn(UrlProbeOutcome.ALIVE);
    }

    private JobUrlCheckService serviceWithScheduleEnabled(boolean enabled) {
        return new JobUrlCheckService(jobRepo, urlProbe, mock(IgnoredJobRepositoryPort.class),
                mock(SavedJobRepositoryPort.class), enabled, 3, 150, 0, true, 3000, 4);
    }

    @Test
    void anExplicitCheckProbesEvenWithTheScheduleSwitchedOff() {
        // Bug: the explicit check honours JOB_URL_CHECK_ENABLED=false, which the scheduled crawl
        // sets to stop the in-process cron, so --crawler.url-check silently probes nothing and
        // expiry then retires live postings.
        serviceWithScheduleEnabled(false).checkDueUrls(4);

        verify(jobRepo).markUrlAlive(jobId);
    }

    @Test
    void anExplicitCheckSizesItsBatchForTheStatedCadence() {
        // Bug: the stated runs per day is ignored and the configured 4 is used, so a crawl that
        // runs 6 times a day probes 1417 postings a run instead of 945.
        when(jobRepo.countActive()).thenReturn(17_000L);

        serviceWithScheduleEnabled(true).checkDueUrls(6);

        verify(jobRepo).findUrlCheckCandidates(any(), eq(945));
    }

    @Test
    void theScheduledTickStillHonoursTheSwitch() {
        // Bug: splitting out the explicit check drops the guard from the scheduled tick, so a
        // server with probing switched off starts probing every six hours.
        serviceWithScheduleEnabled(false).checkJobUrls();

        verify(urlProbe, never()).probe(any());
    }
}
