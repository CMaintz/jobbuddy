package com.autoapplicant.domain.analytics;

import java.util.List;

/**
 * Week-over-week application activity summary, with a 14-day daily time series
 * for rendering a sparkline graph.
 */
public record WeeklyTrend(
        int thisWeek,
        int lastWeek,
        int delta,
        String message,
        List<DailyCount> daily
) {}
