package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.linkedin.LinkedInQueryPlan;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import com.autoapplicant.port.in.linkedin.GenerateLinkedInQueryPlanUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/linkedin")
@Tag(name = "Admin - LinkedIn")
public class LinkedInAdminController {

    private final GenerateLinkedInQueryPlanUseCase queryPlanner;
    private final TriggerCrawlUseCase triggerCrawl;

    public LinkedInAdminController(GenerateLinkedInQueryPlanUseCase queryPlanner,
                                   TriggerCrawlUseCase triggerCrawl) {
        this.queryPlanner = queryPlanner;
        this.triggerCrawl = triggerCrawl;
    }

    @Operation(summary = "(Re)generate the LinkedIn keyword plan for one user from their profile")
    @PostMapping("/plan/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LinkedInQueryPlan> generatePlan(@PathVariable UUID userId) {
        return ResponseEntity.ok(queryPlanner.generateForUser(userId));
    }

    @Operation(summary = "Refresh stale/missing LinkedIn keyword plans for all users")
    @PostMapping("/plan/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LinkedInQueryPlan>> refreshPlans() {
        return ResponseEntity.ok(queryPlanner.ensureFreshPlans());
    }

    @Operation(summary = "Run a LinkedIn crawl now (refreshes plans first, then crawls asynchronously)")
    @ApiResponse(responseCode = "202", description = "Crawl accepted and running asynchronously")
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> runNow() {
        queryPlanner.ensureFreshPlans();
        triggerCrawl.triggerSource(JobSource.LINKEDIN);
        return ResponseEntity.accepted().build();
    }
}
