package com.autoapplicant.adapter.cli;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.crawler.CrawlRunReport;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import com.autoapplicant.port.in.job.CheckJobUrlsUseCase;
import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.in.job.ExpireStaleJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * A one-shot crawl is never alive at 03:00 or on the six-hourly probe tick, so it has to retire
 * dead postings and confirm live ones itself. Each call here changes which postings users see,
 * so the call is the behaviour under test.
 */
class CrawlerCliRunnerMaintenanceTest {

    private final TriggerCrawlUseCase crawl = Mockito.mock(TriggerCrawlUseCase.class);
    private final ExpireStaleJobsUseCase expiry = Mockito.mock(ExpireStaleJobsUseCase.class);
    private final CheckJobUrlsUseCase urlCheck = Mockito.mock(CheckJobUrlsUseCase.class);
    private final EmbedJobsUseCase embedding = Mockito.mock(EmbedJobsUseCase.class);

    CrawlerCliRunnerMaintenanceTest() {
        when(crawl.triggerAllSync()).thenReturn(new CrawlRunReport(List.of()));
        when(embedding.embedPending(anyInt(), any(Duration.class)))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(0, 0, 0, 0));
    }

    private void run(String... args) {
        QueueDrainer drainer = new QueueDrainer(Mockito.mock(SweepUnenrichedJobsUseCase.class), embedding);
        new CrawlerCliRunner(crawl, drainer, expiry, urlCheck, false)
                .run(new DefaultApplicationArguments(args));
    }

    @Test
    void aCrawlRunRetiresStalePostingsAfterCrawling() {
        // Bug: expiry only runs on its 03:00 schedule, which a one-shot process never reaches,
        // so postings taken down weeks ago stay in the feed forever.
        run();

        InOrder order = inOrder(crawl, expiry);
        order.verify(crawl).triggerAllSync();
        order.verify(expiry).deactivateStaleJobs();
    }

    @Test
    void theUrlProbeIsOptInAndRunsBeforeExpiry() {
        // Bug: expiry running first retires postings that connectors stopped revisiting but whose
        // URL still answers, because only the probe refreshes their lastSeenAt.
        run("--crawler.url-check", "--crawler.runs-per-day=6");

        InOrder order = inOrder(urlCheck, expiry);
        order.verify(urlCheck).checkDueUrls(6);
        order.verify(expiry).deactivateStaleJobs();
    }

    @Test
    void theProbeWithoutAStatedCadenceStopsTheRunBeforeCrawling() {
        // Bug: the batch is sized for a default of 4 runs a day; a workflow that runs twice a
        // day silently probes half of what it needs, and expiry then retires live postings.
        assertThatThrownBy(() -> run("--crawler.url-check"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--crawler.runs-per-day");

        verify(crawl, never()).triggerAllSync();
    }

    @Test
    void withoutTheArgTheProbeDoesNotRun() {
        // Bug: probing on every run adds up to 15 minutes of requests to each scheduled crawl.
        run();

        verify(urlCheck, never()).checkDueUrls(anyInt());
    }

    @Test
    void expiryCanBeSwitchedOffAndThenNeitherRuns() {
        // Bug: no way to stop a crawl run from retiring postings, e.g. while a second process
        // already owns expiry, or while a broken connector would make everything look stale.
        run("--crawler.no-expiry");

        verify(expiry, never()).deactivateStaleJobs();
        verify(urlCheck, never()).checkDueUrls(anyInt());
    }
}
