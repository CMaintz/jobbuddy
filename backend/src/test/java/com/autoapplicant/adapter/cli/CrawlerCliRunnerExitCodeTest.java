package com.autoapplicant.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.crawler.CrawlerOrchestrator;
import com.autoapplicant.adapter.crawler.IngestionPipeline;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.out.crawler.CrawlConfig;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.crawler.CrawlManagementService;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * The exit code is how a scheduled crawl (GitHub Actions) learns that a source broke. The
 * orchestrator catches connector failures so one source cannot sink the others, which is
 * exactly why the code has to be derived from the run and not from whether {@code run} threw.
 */
class CrawlerCliRunnerExitCodeTest {

    private final SweepUnenrichedJobsUseCase enrichment = Mockito.mock(SweepUnenrichedJobsUseCase.class);
    private final EmbedJobsUseCase embedding = Mockito.mock(EmbedJobsUseCase.class);

    CrawlerCliRunnerExitCodeTest() {
        when(enrichment.sweep(anyInt())).thenReturn(new SweepUnenrichedJobsUseCase.SweepResult(0, 0, 0, 0));
        when(embedding.embedPending(anyInt(), any(Duration.class)))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(0, 0, 0, 0));
    }

    private static JobSourceConnectorPort connector(JobSource source, Consumer<CrawlConfig> behaviour) {
        JobSourceConnectorPort c = Mockito.mock(JobSourceConnectorPort.class);
        when(c.getSource()).thenReturn(source);
        when(c.includeInDefaultSchedule()).thenReturn(true);
        Mockito.doAnswer(inv -> {
            behaviour.accept(inv.getArgument(0));
            return null;
        }).when(c).fetchJobs(any());
        return c;
    }

    private CrawlerCliRunner runnerOver(JobSourceConnectorPort... connectors) {
        CrawlerOrchestrator orchestrator = new CrawlerOrchestrator(List.of(connectors),
                Mockito.mock(IngestionPipeline.class), Mockito.mock(JobRepositoryPort.class),
                Mockito.mock(CrawlerStateRepositoryPort.class), Runnable::run);
        return new CrawlerCliRunner(new CrawlManagementService(orchestrator),
                new QueueDrainer(enrichment, embedding), true);
    }

    private static int exitCodeAfterRunning(CrawlerCliRunner runner, String... args) {
        runner.run(new DefaultApplicationArguments(args));
        return runner.getExitCode();
    }

    @Test
    void aSourceThatFailsMakesTheRunExitNonZero() {
        // Bug: the orchestrator swallows the connector's exception into crawler_state, so a
        // run whose feed moved or got blocked still exits 0 and the scheduled job shows green.
        JobSourceConnectorPort broken = connector(JobSource.TEAMTAILOR, config -> {
            throw new IllegalStateException("feed moved");
        });
        JobSourceConnectorPort fine = connector(JobSource.LEVER, config -> { });

        assertThat(exitCodeAfterRunning(runnerOver(broken, fine))).isEqualTo(1);
    }

    @Test
    void aTargetedRunOfAFailingSourceExitsNonZero() {
        // Bug: only the all-sources path reports failures, so the per-source step a workflow
        // uses (--crawler.source=X) stays green when that one source fails.
        JobSourceConnectorPort broken = connector(JobSource.LEVER, config -> {
            throw new IllegalStateException("404");
        });

        assertThat(exitCodeAfterRunning(runnerOver(broken), "--crawler.source=lever")).isEqualTo(1);
    }

    @Test
    void aRunWhereEverySourceFinishesExitsZero() {
        // Bug: a flipped or always-on failure check turns every scheduled run red, and a red
        // that is always on gets ignored, hiding the real failures above.
        JobSourceConnectorPort fine = connector(JobSource.LEVER, config -> { });
        JobSourceConnectorPort alsoFine = connector(JobSource.TEAMTAILOR, config -> { });

        assertThat(exitCodeAfterRunning(runnerOver(fine, alsoFine))).isZero();
    }

    @Test
    void aSourceThatFailsWithoutAMessageStillExitsNonZero() {
        // Bug: failure is inferred from the stored error message, and an exception with no
        // message (a bare NullPointerException) stores null, so the run exits 0.
        JobSourceConnectorPort broken = connector(JobSource.LEVER, config -> {
            throw new IllegalStateException();
        });

        assertThat(exitCodeAfterRunning(runnerOver(broken))).isEqualTo(1);
    }

    @Test
    void aRunWhereNoPostingCouldBeEmbeddedExitsNonZero() {
        // Bug: with a missing or revoked embedding key every attempt fails, yet the run exits 0;
        // four green runs later every new posting is given up on for good.
        when(embedding.embedPending(anyInt(), any(Duration.class)))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(3, 0, 3, 0));

        assertThat(exitCodeAfterRunning(runnerOver(connector(JobSource.LEVER, config -> { }))))
                .isEqualTo(1);
    }

    @Test
    void aRunThatGivesUpOnEveryEmbeddingExitsNonZero() {
        // Bug: giving up counts as progress, so the run that retires the postings for good is
        // the one that shows green.
        when(embedding.embedPending(anyInt(), any(Duration.class)))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(3, 0, 0, 3))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(0, 0, 0, 0));

        assertThat(exitCodeAfterRunning(runnerOver(connector(JobSource.LEVER, config -> { }))))
                .isEqualTo(1);
    }

    @Test
    void aRunWhereSomeEmbeddingsFailStillExitsZero() {
        // Bug: one unembeddable posting (empty text, a provider hiccup) fails every run, and a
        // red that is always on gets ignored.
        when(embedding.embedPending(anyInt(), any(Duration.class)))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(3, 2, 1, 0))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(0, 0, 0, 0));

        assertThat(exitCodeAfterRunning(runnerOver(connector(JobSource.LEVER, config -> { }))))
                .isZero();
    }

    @Test
    void askingForASourceWithNoConnectorExitsNonZero() {
        // Bug: a disabled or unregistered connector only logs a warning, so a workflow step
        // that crawls nothing at all reports success.
        JobSourceConnectorPort lever = connector(JobSource.LEVER, config -> { });

        assertThat(exitCodeAfterRunning(runnerOver(lever), "--crawler.source=JOBINDEX")).isEqualTo(1);
    }
}
