package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.analytics.DashboardData;
import com.autoapplicant.port.in.analytics.GetDashboardUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final GetDashboardUseCase getDashboard;
    private final SecurityContextHelper secCtx;

    public DashboardController(GetDashboardUseCase getDashboard, SecurityContextHelper secCtx) {
        this.getDashboard = getDashboard;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get dashboard summary data")
    @GetMapping
    public ResponseEntity<DashboardData> dashboard() {
        return ResponseEntity.ok(getDashboard.getDashboard(secCtx.getCurrentUserId()));
    }
}
