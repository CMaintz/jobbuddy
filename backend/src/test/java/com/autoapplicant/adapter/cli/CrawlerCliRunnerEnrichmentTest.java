package com.autoapplicant.adapter.cli;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * A scheduled crawl runs where there is no Claude login, so enrichment (one chat call per
 * posting) must be switchable off there while embedding still runs. The sweep call is the spend,
 * which is why these tests check it was or was not made.
 */
class CrawlerCliRunnerEnrichmentTest {

    private final TriggerCrawlUseCase crawl = Mockito.mock(TriggerCrawlUseCase.class);
    private final SweepUnenrichedJobsUseCase enrichment = Mockito.mock(SweepUnenrichedJobsUseCase.class);
    private final EmbedJobsUseCase embedding = Mockito.mock(EmbedJobsUseCase.class);

    CrawlerCliRunnerEnrichmentTest() {
        when(crawl.triggerAllSync()).thenReturn(new CrawlRunReport(List.of()));
        when(enrichment.sweep(anyInt())).thenReturn(new SweepUnenrichedJobsUseCase.SweepResult(0, 0, 0, 0));
        when(embedding.embedPending(anyInt(), any(Duration.class)))
                .thenReturn(new EmbedJobsUseCase.EmbedResult(0, 0, 0, 0));
    }

    private void runWithEnrichment(boolean enabled) {
        new CrawlerCliRunner(crawl, new QueueDrainer(enrichment, embedding),
                Mockito.mock(ExpireStaleJobsUseCase.class), Mockito.mock(CheckJobUrlsUseCase.class), enabled)
                .run(new DefaultApplicationArguments());
    }

    @Test
    void withEnrichmentOffTheRunMakesNoEnrichmentCallsButStillEmbeds() {
        // Bug: the drain ignores the flag and enriches every new posting. Without a key each
        // failure still counts, so a few cron runs give up on postings before the desktop app
        // ever gets to enrich them; with a key it spends on the cron's account.
        runWithEnrichment(false);

        verify(enrichment, never()).sweep(anyInt());
        verify(embedding).embedPending(anyInt(), any(Duration.class));
    }

    @Test
    void withEnrichmentOnTheRunStillEnriches() {
        // Bug: a flipped flag stops the server-side crawl enriching anything.
        runWithEnrichment(true);

        verify(enrichment).sweep(anyInt());
    }
}
