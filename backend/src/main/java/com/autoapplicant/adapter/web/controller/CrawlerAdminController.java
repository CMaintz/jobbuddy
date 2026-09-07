package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/crawler")
@Tag(name = "Admin - Crawler")
public class CrawlerAdminController {

    private final TriggerCrawlUseCase triggerCrawl;

    public CrawlerAdminController(TriggerCrawlUseCase triggerCrawl) {
        this.triggerCrawl = triggerCrawl;
    }

    @Operation(summary = "Trigger crawl for all sources")
    @ApiResponse(responseCode = "202", description = "Crawl accepted and running asynchronously")
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> triggerAll() {
        triggerCrawl.triggerAll();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Trigger crawl for a specific source")
    @ApiResponse(responseCode = "202", description = "Crawl accepted and running asynchronously")
    @PostMapping("/trigger/{source}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> triggerSource(@PathVariable String source) {
        triggerCrawl.triggerSource(JobSource.valueOf(source.toUpperCase()));
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Force-crawl all sources (ignores already-known pages, use for backfills)")
    @ApiResponse(responseCode = "202", description = "Crawl accepted and running asynchronously")
    @PostMapping("/force-trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> forceTriggerAll() {
        triggerCrawl.triggerAllForce();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Force-crawl a specific source (ignores already-known pages)")
    @ApiResponse(responseCode = "202", description = "Crawl accepted and running asynchronously")
    @PostMapping("/force-trigger/{source}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> forceTriggerSource(@PathVariable String source) {
        triggerCrawl.triggerSourceForce(JobSource.valueOf(source.toUpperCase()));
        return ResponseEntity.accepted().build();
    }
}
