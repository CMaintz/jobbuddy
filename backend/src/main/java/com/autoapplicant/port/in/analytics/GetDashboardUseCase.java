package com.autoapplicant.port.in.analytics;

import com.autoapplicant.domain.analytics.DashboardData;

import java.util.UUID;

public interface GetDashboardUseCase {
    DashboardData getDashboard(UUID userId);
}
