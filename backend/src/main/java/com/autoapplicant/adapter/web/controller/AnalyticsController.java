package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.analytics.ApplicationMetrics;
import com.autoapplicant.domain.analytics.DetailedMetrics;
import com.autoapplicant.domain.analytics.WeeklyTrend;
import com.autoapplicant.port.in.analytics.GetApplicationMetricsUseCase;
import com.autoapplicant.port.in.analytics.GetDetailedMetricsUseCase;
import com.autoapplicant.port.in.analytics.GetWeeklyTrendUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final GetApplicationMetricsUseCase getMetrics;
    private final GetDetailedMetricsUseCase getDetailedMetrics;
    private final GetWeeklyTrendUseCase getWeeklyTrend;
    private final SecurityContextHelper secCtx;

    public AnalyticsController(GetApplicationMetricsUseCase getMetrics,
                                GetDetailedMetricsUseCase getDetailedMetrics,
                                GetWeeklyTrendUseCase getWeeklyTrend,
                                SecurityContextHelper secCtx) {
        this.getMetrics = getMetrics;
        this.getDetailedMetrics = getDetailedMetrics;
        this.getWeeklyTrend = getWeeklyTrend;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get application metrics")
    @GetMapping
    public ResponseEntity<ApplicationMetrics> metrics() {
        return ResponseEntity.ok(getMetrics.getMetrics(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Get detailed analytics metrics")
    @GetMapping("/detailed")
    public ResponseEntity<DetailedMetrics> detailedMetrics() {
        return ResponseEntity.ok(getDetailedMetrics.getDetailedMetrics(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Get week-over-week application trend with 14-day daily sparkline data")
    @GetMapping("/trend")
    public ResponseEntity<WeeklyTrend> weeklyTrend() {
        return ResponseEntity.ok(getWeeklyTrend.getWeeklyTrend(secCtx.getCurrentUserId()));
    }
}
