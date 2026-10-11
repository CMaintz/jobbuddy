package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActiveCrawlsTest {

    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final CrawlerStateRepositoryPort repo = mock(CrawlerStateRepositoryPort.class);
    private final ActiveCrawls activeCrawls = new ActiveCrawls(repo, STALE_AFTER);

    private static CrawlerState state(String source, boolean running, Instant updatedAt) {
        return new CrawlerState(source, 0, updatedAt, null, 0, 0, null, running, updatedAt);
    }

    @Test
    void startupClearsRunningFlagsLeftByThePreviousProcess() {
        // A fresh JVM has no crawl in progress, so any flag still set was left by a crash.
        when(repo.findAll()).thenReturn(List.of(
                state("TEAMTAILOR", true, Instant.now()),
                state("GREENHOUSE", false, Instant.now())));

        activeCrawls.clearLeftoverRunningFlags();

        ArgumentCaptor<CrawlerState> saved = ArgumentCaptor.forClass(CrawlerState.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().source()).isEqualTo("TEAMTAILOR");
        assertThat(saved.getValue().isRunning()).isFalse();
    }

    @Test
    void startupLeavesIdleSourcesAlone() {
        when(repo.findAll()).thenReturn(List.of(state("GREENHOUSE", false, Instant.now())));

        activeCrawls.clearLeftoverRunningFlags();

        verify(repo, never()).save(any());
    }

    @Test
    void aFreshRunningFlagCountsAsRunning() {
        when(repo.findAll()).thenReturn(List.of(state("TEAMTAILOR", true, Instant.now())));

        assertThat(activeCrawls.anyRunning()).isTrue();
    }

    @Test
    void aStaleRunningFlagIsIgnored() {
        Instant longAgo = Instant.now().minus(STALE_AFTER).minusSeconds(60);
        when(repo.findAll()).thenReturn(List.of(state("TEAMTAILOR", true, longAgo)));

        assertThat(activeCrawls.anyRunning()).isFalse();
    }
}
