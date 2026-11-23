package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import com.autoapplicant.port.out.crawler.JobSourceConnectorPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A cold crawl runs longer than {@code stale-running-after}. Its running flag has to stay fresh
 * for the whole run, or another process (a desktop launch, the next scheduled run) judges it
 * stale, clears it and starts the sweeps alongside it.
 */
class CrawlHeartbeatTest {

    private static final Duration STALE_AFTER = Duration.ofMillis(400);
    private static final long CRAWL_MILLIS = 900;

    /** The crawler_state table, in memory. */
    private static final class StateTable implements CrawlerStateRepositoryPort {
        final Map<String, CrawlerState> rows = new ConcurrentHashMap<>();

        @Override
        public CrawlerState save(CrawlerState state) {
            CrawlerState stamped = new CrawlerState(state.source(), state.pageOffset(),
                    state.lastCrawlStartedAt(), state.lastCrawlFinishedAt(), state.jobsFound(),
                    state.jobsIngested(), state.lastError(), state.isRunning(), Instant.now());
            rows.put(state.source(), stamped);
            return stamped;
        }

        @Override
        public Optional<CrawlerState> findBySource(String source) {
            return Optional.ofNullable(rows.get(source));
        }

        @Override
        public List<CrawlerState> findAll() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void heartbeat(String source, Instant at) {
            rows.computeIfPresent(source, (s, row) -> row.isRunning()
                    ? new CrawlerState(row.source(), row.pageOffset(), row.lastCrawlStartedAt(),
                            row.lastCrawlFinishedAt(), row.jobsFound(), row.jobsIngested(),
                            row.lastError(), true, at)
                    : row);
        }
    }

    @Test
    void aCrawlRunningLongerThanTheStaleThresholdStillCountsAsRunning() throws Exception {
        // Bug: the flag is written once when the crawl starts, so a crawl that outlives
        // stale-running-after looks abandoned while it is still writing postings.
        StateTable table = new StateTable();
        ActiveCrawls activeCrawls = new ActiveCrawls(table, STALE_AFTER);
        AtomicBoolean seenRunningLate = new AtomicBoolean();
        JobSourceConnectorPort connector = Mockito.mock(JobSourceConnectorPort.class);
        when(connector.getSource()).thenReturn(JobSource.LEVER);
        Mockito.doAnswer(inv -> {
            Thread.sleep(CRAWL_MILLIS);
            seenRunningLate.set(activeCrawls.anyRunning());
            return null;
        }).when(connector).fetchJobs(any());

        new CrawlerOrchestrator(List.of(connector), Mockito.mock(IngestionPipeline.class),
                Mockito.mock(JobRepositoryPort.class), table, Runnable::run, STALE_AFTER)
                .runConnector(connector);

        assertThat(seenRunningLate).isTrue();
        assertThat(table.rows.get("LEVER").isRunning()).isFalse();
    }

    @Test
    void startupKeepsAFlagWithAFreshHeartbeatHoweverLongAgoTheCrawlStarted() {
        // Bug: staleness is judged from the start time, so the startup clear removes the flag
        // of a crawl that started three hours ago and is still heartbeating.
        StateTable table = new StateTable();
        Instant now = Instant.now();
        table.rows.put("JOBINDEX", new CrawlerState("JOBINDEX", 0, now.minus(Duration.ofHours(3)),
                null, 0, 0, null, true, now.minus(Duration.ofMinutes(10))));

        new ActiveCrawls(table, Duration.ofHours(2)).clearLeftoverRunningFlags();

        assertThat(table.rows.get("JOBINDEX").isRunning()).isTrue();
    }
}
