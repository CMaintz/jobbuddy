package com.autoapplicant.domain.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CrawlerStateTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private static CrawlerState running(boolean running, Instant updatedAt) {
        return new CrawlerState("TEAMTAILOR", 0, updatedAt, null, 0, 0, null, running, updatedAt);
    }

    @Test
    void aRecentRunningFlagIsBelieved() {
        assertThat(running(true, NOW.minus(Duration.ofMinutes(5)))
                .isActivelyRunning(NOW, STALE_AFTER)).isTrue();
    }

    @Test
    void aRunningFlagOlderThanTheCutoffIsTakenAsLeftBehind() {
        // A crawl that died mid-run never clears its flag; it must not read as running forever.
        assertThat(running(true, NOW.minus(STALE_AFTER).minusSeconds(1))
                .isActivelyRunning(NOW, STALE_AFTER)).isFalse();
    }

    @Test
    void aCrawlThatIsNotRunningIsNeverActive() {
        assertThat(running(false, NOW).isActivelyRunning(NOW, STALE_AFTER)).isFalse();
    }

    @Test
    void theStartTimeStandsInWhenUpdatedAtIsMissing() {
        CrawlerState state = new CrawlerState("TEAMTAILOR", 0, NOW.minusSeconds(60), null,
                0, 0, null, true, null);

        assertThat(state.isActivelyRunning(NOW, STALE_AFTER)).isTrue();
    }

    @Test
    void notRunningClearsOnlyTheFlag() {
        CrawlerState state = running(true, NOW);

        CrawlerState cleared = state.notRunning();

        assertThat(cleared.isRunning()).isFalse();
        assertThat(cleared.source()).isEqualTo(state.source());
        assertThat(cleared.lastCrawlStartedAt()).isEqualTo(state.lastCrawlStartedAt());
    }
}
