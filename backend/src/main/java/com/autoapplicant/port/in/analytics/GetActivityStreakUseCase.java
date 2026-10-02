package com.autoapplicant.port.in.analytics;

import com.autoapplicant.domain.analytics.ActivityStreak;
import java.time.ZoneId;
import java.util.UUID;

public interface GetActivityStreakUseCase {
    ActivityStreak getActivityStreak(UUID userId, ZoneId zone);
}
