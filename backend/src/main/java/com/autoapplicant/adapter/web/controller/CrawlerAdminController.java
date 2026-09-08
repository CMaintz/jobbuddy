package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.crawler.CrawlerState;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
import com.autoapplicant.port.out.crawler.CrawlerStateRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/crawler")
@Tag(name = "Admin - Crawler")
public class CrawlerAdminController {

    private final TriggerCrawlUseCase triggerCrawl;
    private final CrawlerStateRepositoryPort crawlerStateRepo;

    public CrawlerAdminController(TriggerCrawlUseCase triggerCrawl,
                                   CrawlerStateRepositoryPort crawlerStateRepo) {
        this.triggerCrawl = triggerCrawl;
        this.crawlerStateRepo = crawlerStateRepo;
    }

    @Operation(summary = "Get crawl status for all sources")
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CrawlerState>> getStatus() {
        return ResponseEntity.ok(crawlerStateRepo.findAll());
    }

    @Operation(summary = "Get crawl status for a specific source")
    @GetMapping("/status/{source}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CrawlerState> getSourceStatus(@PathVariable String source) {
        return crawlerStateRepo.findBySource(source.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
