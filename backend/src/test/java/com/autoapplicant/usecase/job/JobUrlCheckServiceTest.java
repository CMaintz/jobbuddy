package com.autoapplicant.usecase.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.UrlProbeOutcome;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.JobUrlProbePort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobUrlCheckServiceTest {

    private static final int RECHECK_DAYS = 3;
    private static final int BATCH_SIZE = 150;
    private static final int MAX_BATCH_SIZE = 3000;
    private static final int RUNS_PER_DAY = 4;
    private static final long ASYNC_WAIT_MS = 2_000;
    private static final String URL = "https://jobs.example.dk/123";

    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final JobUrlProbePort urlProbe = mock(JobUrlProbePort.class);
    private final IgnoredJobRepositoryPort ignoredJobRepo = mock(IgnoredJobRepositoryPort.class);
    private final SavedJobRepositoryPort savedJobRepo = mock(SavedJobRepositoryPort.class);

    private final UUID jobId = UUID.randomUUID();

    @Test
    void the_batch_grows_so_the_whole_corpus_is_probed_within_the_recheck_window() {
        // 17,000 postings over 4 runs a day for 3 days: ceil(17000 / 12) per run.
        assertThat(batchProbedFor(17_000, true)).isEqualTo(1417);
    }

    @Test
    void a_small_corpus_still_gets_the_configured_batch() {
        assertThat(batchProbedFor(100, true)).isEqualTo(BATCH_SIZE);
    }
    @Test
    void a_huge_corpus_is_capped_so_a_bad_count_cannot_become_a_runaway_crawl() {
        assertThat(batchProbedFor(100_000, true)).isEqualTo(MAX_BATCH_SIZE);
    }

    @Test
    void with_adaptive_sizing_off_the_configured_batch_is_used_as_is() {
        assertThat(batchProbedFor(17_000, false)).isEqualTo(BATCH_SIZE);
    }

    @Test
    void a_live_posting_is_marked_alive_and_an_unclear_probe_is_only_noted() {
        Job alive = job("https://jobs.example.dk/alive");
        Job unclear = job("https://jobs.example.dk/unclear");
        givenCandidates(alive, unclear);
        when(urlProbe.probe(alive.url())).thenReturn(UrlProbeOutcome.ALIVE);
        when(urlProbe.probe(unclear.url())).thenReturn(UrlProbeOutcome.INCONCLUSIVE);

        service(true).checkJobUrls();

        verify(jobRepo).markUrlAlive(alive.id());
        verify(jobRepo).markUrlCheckInconclusive(unclear.id());
        verify(jobRepo, never()).markUrlTakenDown(any(), anyInt());
    }

    @Test
    void a_hard_404_counts_on_first_sighting_but_a_soft_marker_needs_a_second() {
        Job hard = job("https://jobs.example.dk/gone");
        Job soft = job("https://jobs.example.dk/closed-page");
        givenCandidates(hard, soft);
        when(urlProbe.probe(hard.url())).thenReturn(UrlProbeOutcome.GONE);
        when(urlProbe.probe(soft.url())).thenReturn(UrlProbeOutcome.GONE_SOFT);

        service(true).checkJobUrls();

        verify(jobRepo).markUrlTakenDown(hard.id(), 1);
        verify(jobRepo).markUrlTakenDown(soft.id(), 2);
    }

    @Test
    void a_posting_without_a_url_is_never_probed() {
        givenCandidates(job(" "));

        service(true).checkJobUrls();

        verify(urlProbe, never()).probe(anyString());
    }

    @Test
    void reporting_a_posting_hides_it_for_the_reporter_and_leaves_everyone_else_to_the_probe() {
        UUID reporter = UUID.randomUUID();
        Job posting = job(URL);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(posting));
        when(urlProbe.probe(URL)).thenReturn(UrlProbeOutcome.ALIVE);

        service(true).reportInactive(reporter, jobId);

        verify(savedJobRepo).unsave(reporter, jobId);
        ArgumentCaptor<IgnoredJob> ignored = ArgumentCaptor.forClass(IgnoredJob.class);
        verify(ignoredJobRepo).save(ignored.capture());
        assertThat(ignored.getValue().userId()).isEqualTo(reporter);
        assertThat(ignored.getValue().jobId()).isEqualTo(jobId);
        // The probe says the posting is up, so one user's report must not take it down for all.
        verify(jobRepo, timeout(ASYNC_WAIT_MS)).markUrlAlive(jobId);
        verify(jobRepo, never()).markUrlTakenDown(any(), anyInt());
    }

    @Test
    void reporting_a_posting_already_hidden_does_not_hide_it_twice() {
        UUID reporter = UUID.randomUUID();
        when(ignoredJobRepo.isIgnored(reporter, jobId)).thenReturn(true);
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());

        service(true).reportInactive(reporter, jobId);

        verify(ignoredJobRepo, never()).save(any());
    }

    private int batchProbedFor(long activePostings, boolean adaptive) {
        when(jobRepo.countActive()).thenReturn(activePostings);
        when(jobRepo.findUrlCheckCandidates(any(), anyInt())).thenReturn(List.of());

        service(adaptive).checkJobUrls();

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(jobRepo).findUrlCheckCandidates(any(), limit.capture());
        return limit.getValue();
    }

    private void givenCandidates(Job... jobs) {
        when(jobRepo.findUrlCheckCandidates(any(), anyInt())).thenReturn(List.of(jobs));
    }

    private Job job(String url) {
        UUID id = url.equals(URL) ? jobId : UUID.randomUUID();
        return Job.builder().id(id).title("Role").url(url).build();
    }

    /** No politeness delay, so a run over a few candidates does not sleep. */
    private JobUrlCheckService service(boolean adaptive) {
        return new JobUrlCheckService(jobRepo, urlProbe, ignoredJobRepo, savedJobRepo,
                true, RECHECK_DAYS, BATCH_SIZE, 0, adaptive, MAX_BATCH_SIZE, RUNS_PER_DAY);
    }
}
