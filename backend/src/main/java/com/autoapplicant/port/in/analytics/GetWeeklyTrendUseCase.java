package com.autoapplicant.port.in.analytics;

import com.autoapplicant.domain.analytics.WeeklyTrend;

import java.util.UUID;

public interface GetWeeklyTrendUseCase {
    WeeklyTrend getWeeklyTrend(UUID userId);
}
