package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.crawler.TriggerCrawlUseCase;
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

    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerAll() {
        triggerCrawl.triggerAll();
        return ResponseEntity.ok("Crawl triggered for all sources");
    }

    @PostMapping("/trigger/{source}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerSource(@PathVariable String source) {
        triggerCrawl.triggerSource(JobSource.valueOf(source.toUpperCase()));
        return ResponseEntity.ok("Crawl triggered for: " + source);
    }
}
