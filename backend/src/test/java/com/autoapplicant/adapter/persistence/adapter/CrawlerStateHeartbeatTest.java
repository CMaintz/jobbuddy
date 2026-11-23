package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.crawler.CrawlerState;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(CrawlerStatePersistenceAdapter.class)
class CrawlerStateHeartbeatTest extends PostgresIntegrationTest {

    private static final int FOUND = 120;

    @Autowired CrawlerStatePersistenceAdapter states;
    @Autowired EntityManager entityManager;

    private CrawlerState reload(String source) {
        entityManager.flush();
        entityManager.clear();
        return states.findBySource(source).orElseThrow();
    }

    @Test
    void aHeartbeatFreshensTheFlagAndTouchesNothingElse() {
        // Bug: the heartbeat writes the state as the crawl knew it at the start, resetting the
        // counters and error another write has recorded since; or it never reaches the row.
        // Millisecond precision so the stored value is exact: Postgres keeps microseconds and
        // rounds rather than truncates, so a nanosecond Instant could come back one micro off.
        Instant started = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        states.save(new CrawlerState("JOBINDEX", 0, started, null, FOUND, 7, null, true, started));
        Instant beat = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        states.heartbeat("JOBINDEX", beat);

        CrawlerState after = reload("JOBINDEX");
        assertThat(after.updatedAt()).isEqualTo(beat);
        assertThat(after.isRunning()).isTrue();
        assertThat(after.jobsFound()).isEqualTo(FOUND);
        assertThat(after.lastCrawlStartedAt()).isEqualTo(started);
    }

    @Test
    void aHeartbeatAfterTheCrawlFinishedDoesNotMarkItRunning() {
        // Bug: a heartbeat that lands just after the crawl finished revives its running flag,
        // and the sweeps stand down until the flag goes stale two hours later.
        Instant started = Instant.now().minus(1, ChronoUnit.HOURS);
        states.save(new CrawlerState("LEVER", 0, started, Instant.now(), 5, 5, null, false, null));

        states.heartbeat("LEVER", Instant.now());

        assertThat(reload("LEVER").isRunning()).isFalse();
    }
}
