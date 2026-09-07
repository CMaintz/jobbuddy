package com.autoapplicant.port.in.analytics;

import com.autoapplicant.domain.analytics.ApplicationMetrics;

import java.util.UUID;

public interface GetApplicationMetricsUseCase {
    ApplicationMetrics getMetrics(UUID userId);
}
