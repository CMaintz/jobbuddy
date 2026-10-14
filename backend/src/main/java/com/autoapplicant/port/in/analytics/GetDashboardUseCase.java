package com.autoapplicant.port.in.analytics;

import com.autoapplicant.domain.analytics.DashboardData;
import com.autoapplicant.domain.matching.MatchResult;
import java.util.List;
import java.util.UUID;

public interface GetDashboardUseCase {
    /** Builds the dashboard around recommendations the caller has already fetched. */
    DashboardData getDashboard(UUID userId, List<MatchResult> recommendations);
}
