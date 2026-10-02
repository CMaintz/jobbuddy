package com.autoapplicant.usecase.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.port.out.analytics.JobSearchActivityPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityStreakServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");

    private final FakeActivity activity = new FakeActivity();

    @Test
    void today_and_the_day_boundaries_follow_the_users_zone() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T23:30:00Z"), ZoneOffset.UTC);
        activity.events.add(Instant.parse("2026-09-29T10:00:00Z"));
        activity.events.add(Instant.parse("2026-09-30T23:00:00Z"));

        // Copenhagen: the second event is 1 Oct 01:00, so 30 Sep is a gap.
        assertThat(service(clock).getActivityStreak(USER, CPH).days()).isEqualTo(1);
        // UTC: both events are on consecutive days and today is 30 Sep.
        assertThat(service(clock).getActivityStreak(USER, ZoneOffset.UTC).days()).isEqualTo(2);
    }

    @Test
    void looks_back_from_local_midnight_at_the_start_of_the_window() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

        service(clock).getActivityStreak(USER, CPH);

        assertThat(activity.askedFor).isEqualTo(USER);
        // 400 days before 1 Oct 2026 is 27 Aug 2025, which starts at 22:00 UTC the day before (CEST).
        assertThat(activity.askedSince).isEqualTo(Instant.parse("2025-08-26T22:00:00Z"));
    }

    @Test
    void no_activity_is_zero() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

        assertThat(service(clock).getActivityStreak(USER, CPH).days()).isZero();
    }

    private ActivityStreakService service(Clock clock) {
        return new ActivityStreakService(activity, clock);
    }

    private static final class FakeActivity implements JobSearchActivityPort {
        final List<Instant> events = new ArrayList<>();
        UUID askedFor;
        Instant askedSince;

        @Override
        public List<Instant> findActivitySince(UUID userId, Instant since) {
            askedFor = userId;
            askedSince = since;
            return events;
        }
    }
}
