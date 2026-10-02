package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.analytics.ActivityStreak;
import com.autoapplicant.domain.analytics.ApplicationMetrics;
import com.autoapplicant.domain.analytics.DetailedMetrics;
import com.autoapplicant.domain.analytics.FunnelVelocity;
import com.autoapplicant.domain.analytics.WeeklyTrend;
import com.autoapplicant.port.in.analytics.GetActivityStreakUseCase;
import com.autoapplicant.port.in.analytics.GetApplicationMetricsUseCase;
import com.autoapplicant.port.in.analytics.GetDetailedMetricsUseCase;
import com.autoapplicant.port.in.analytics.GetFunnelVelocityUseCase;
import com.autoapplicant.port.in.analytics.GetWeeklyTrendUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final GetApplicationMetricsUseCase getMetrics;
    private final GetDetailedMetricsUseCase getDetailedMetrics;
    private final GetWeeklyTrendUseCase getWeeklyTrend;
    private final GetFunnelVelocityUseCase getFunnelVelocity;
    private final GetActivityStreakUseCase getActivityStreak;
    private final SecurityContextHelper secCtx;

    public AnalyticsController(GetApplicationMetricsUseCase getMetrics,
                                GetDetailedMetricsUseCase getDetailedMetrics,
                                GetWeeklyTrendUseCase getWeeklyTrend,
                                GetFunnelVelocityUseCase getFunnelVelocity,
                                GetActivityStreakUseCase getActivityStreak,
                                SecurityContextHelper secCtx) {
        this.getMetrics = getMetrics;
        this.getDetailedMetrics = getDetailedMetrics;
        this.getWeeklyTrend = getWeeklyTrend;
        this.getFunnelVelocity = getFunnelVelocity;
        this.getActivityStreak = getActivityStreak;
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

    @Operation(summary = "Average time-in-stage between application status transitions (funnel velocity)")
    @GetMapping("/funnel-velocity")
    public ResponseEntity<FunnelVelocity> funnelVelocity() {
        return ResponseEntity.ok(getFunnelVelocity.getFunnelVelocity(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Consecutive days with job-search activity, counted in the caller's time zone")
    @ApiResponse(responseCode = "400", description = "Unknown time zone")
    @GetMapping("/streak")
    public ResponseEntity<ActivityStreak> streak(@RequestParam(defaultValue = "UTC") String zone) {
        return ResponseEntity.ok(getActivityStreak.getActivityStreak(secCtx.getCurrentUserId(), parseZone(zone)));
    }

    private static ZoneId parseZone(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown time zone: " + zone, e);
        }
    }
}
