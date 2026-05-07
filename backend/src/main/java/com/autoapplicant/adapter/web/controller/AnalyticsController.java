package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.analytics.ApplicationMetrics;
import com.autoapplicant.port.in.analytics.GetApplicationMetricsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final GetApplicationMetricsUseCase getMetrics;
    private final SecurityContextHelper secCtx;

    public AnalyticsController(GetApplicationMetricsUseCase getMetrics, SecurityContextHelper secCtx) {
        this.getMetrics = getMetrics;
        this.secCtx = secCtx;
    }

    @GetMapping
    public ResponseEntity<ApplicationMetrics> metrics() {
        return ResponseEntity.ok(getMetrics.getMetrics(secCtx.getCurrentUserId()));
    }
}
