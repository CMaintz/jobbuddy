package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A crawl's two jobs beyond finding new postings: telling the app which existing postings are
 * still live, and reporting honestly what it did.
 */
class CrawlerOrchestratorTest {

    private final IngestionPipeline pipeline = Mockito.mock(IngestionPipeline.class);
    private final JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
    private final CrawlerStateRepositoryPort stateRepo = Mockito.mock(CrawlerStateRepositoryPort.class);

    /** Runs submitted work on the calling thread so the test needs no waiting. */
    private final java.util.concurrent.Executor inline = Runnable::run;

    private static RawJobData raw(String id) {
        return new RawJobData(JobSource.TEAMTAILOR, id, "https://example.com/" + id,
                "<p>body</p>", null, Instant.now(), List.of(), null);
    }

    /** A connector that emits one new posting and recognises one it has seen before. */
    private JobSourceConnectorPort connector(Consumer<CrawlConfig> behaviour) {
        JobSourceConnectorPort c = Mockito.mock(JobSourceConnectorPort.class);
        when(c.getSource()).thenReturn(JobSource.TEAMTAILOR);
        Mockito.doAnswer(inv -> {
            behaviour.accept(inv.getArgument(0));
            return null;
        }).when(c).fetchJobs(any());
        return c;
    }

    private CrawlerOrchestrator orchestratorFor(JobSourceConnectorPort connector) {
        return new CrawlerOrchestrator(List.of(connector), pipeline, jobRepo, stateRepo, inline);
    }

    @Test
    void aRecognisedPostingIsStillMarkedSeen() {
        // The whole point: skipping the re-fetch must not look like the posting disappeared.
        // lastSeenAt drives the expiry sweep, so a silent skip retires a live, advertised role.
        JobSourceConnectorPort connector = connector(config ->
                config.onKnownJobSeen().accept("known-guid-1"));

        orchestratorFor(connector).runConnector(connector);

        verify(jobRepo).markSeenBySourceJobId(eq(JobSource.TEAMTAILOR), eq("known-guid-1"), any());
        verify(pipeline, never()).ingest(any());
    }

    @Test
    void theSummaryDistinguishesNewFromMerelySeen() {
        when(pipeline.ingest(any()))
                .thenReturn(IngestionPipeline.IngestOutcome.NEW)
                .thenReturn(IngestionPipeline.IngestOutcome.REFRESHED);

        JobSourceConnectorPort connector = connector(config -> {
            config.onJobFound().accept(raw("new-1"));       // NEW
            config.onJobFound().accept(raw("existing-1"));  // REFRESHED
            config.onKnownJobSeen().accept("known-1");      // skipped by the connector
            config.onKnownJobSeen().accept("known-2");
        });

        orchestratorFor(connector).runConnector(connector);

        ArgumentCaptor<CrawlerState> saved = ArgumentCaptor.forClass(CrawlerState.class);
        verify(stateRepo, Mockito.atLeastOnce()).save(saved.capture());
        CrawlerState finished = saved.getAllValues().get(saved.getAllValues().size() - 1);

        // Four postings were seen; only one of them was new. These used to be the same number,
        // which made a re-crawl of unchanged postings read as a fresh haul.
        assertThat(finished.jobsFound()).isEqualTo(4);
        assertThat(finished.jobsIngested()).isEqualTo(1);
        assertThat(finished.isRunning()).isFalse();
    }

    @Test
    void aConnectorThatReEmitsEverythingReportsNothingNew() {
        // Greenhouse's shape: it never consults isKnownGuid, so every posting comes through
        // ingest and comes back REFRESHED on a re-crawl. That is correct — it is what keeps
        // lastSeenAt fresh — and the summary should say "0 new", not "50 collected".
        when(pipeline.ingest(any())).thenReturn(IngestionPipeline.IngestOutcome.REFRESHED);

        JobSourceConnectorPort connector = connector(config -> {
            for (int i = 0; i < 50; i++) config.onJobFound().accept(raw("gh-" + i));
        });

        orchestratorFor(connector).runConnector(connector);

        ArgumentCaptor<CrawlerState> saved = ArgumentCaptor.forClass(CrawlerState.class);
        verify(stateRepo, Mockito.atLeastOnce()).save(saved.capture());
        CrawlerState finished = saved.getAllValues().get(saved.getAllValues().size() - 1);

        assertThat(finished.jobsFound()).isEqualTo(50);
        assertThat(finished.jobsIngested()).isZero();
        verify(jobRepo, never()).markSeenBySourceJobId(any(), anyString(), any());
    }
}
