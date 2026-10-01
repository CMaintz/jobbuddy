package com.autoapplicant.usecase.analytics;

import com.autoapplicant.domain.analytics.ActivityStreak;
import com.autoapplicant.port.in.analytics.GetActivityStreakUseCase;
import com.autoapplicant.port.out.analytics.JobSearchActivityPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ActivityStreakService implements GetActivityStreakUseCase {

    /** How far back to look. Streaks longer than this are reported as this long. */
    static final int LOOKBACK_DAYS = 400;

    private final JobSearchActivityPort activity;
    private final Clock clock;

    public ActivityStreakService(JobSearchActivityPort activity, Clock clock) {
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    public ActivityStreak getActivityStreak(UUID userId, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        Instant since = today.minusDays(LOOKBACK_DAYS).atStartOfDay(zone).toInstant();
        List<Instant> events = activity.findActivitySince(userId, since);
        return ActivityStreak.of(events, zone, today);
    }
}
