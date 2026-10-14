package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.analytics.DashboardData;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.port.in.analytics.GetDashboardUseCase;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private static final int DASHBOARD_RECOMMENDATIONS = 5;

    private final GetDashboardUseCase getDashboard;
    private final GetRecommendationsUseCase getRecommendations;
    private final SecurityContextHelper secCtx;

    public DashboardController(GetDashboardUseCase getDashboard,
                               GetRecommendationsUseCase getRecommendations,
                               SecurityContextHelper secCtx) {
        this.getDashboard = getDashboard;
        this.getRecommendations = getRecommendations;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get dashboard summary data")
    @GetMapping
    public ResponseEntity<DashboardData> dashboard() {
        UUID userId = secCtx.getCurrentUserId();
        List<MatchResult> recommendations =
                getRecommendations.getRecommendations(userId, DASHBOARD_RECOMMENDATIONS);
        return ResponseEntity.ok(getDashboard.getDashboard(userId, recommendations));
    }
}
