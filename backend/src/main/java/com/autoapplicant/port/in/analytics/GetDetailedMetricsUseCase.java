package com.autoapplicant.port.in.analytics;

import com.autoapplicant.domain.analytics.DetailedMetrics;

import java.util.UUID;

public interface GetDetailedMetricsUseCase {
    DetailedMetrics getDetailedMetrics(UUID userId);
}
