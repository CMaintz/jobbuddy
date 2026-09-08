package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "crawler_state")
public class CrawlerStateEntity {

    @Id
    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "page_offset", nullable = false)
    private int pageOffset;

    @Column(name = "last_crawl_started_at")
    private Instant lastCrawlStartedAt;

    @Column(name = "last_crawl_finished_at")
    private Instant lastCrawlFinishedAt;

    @Column(name = "jobs_found", nullable = false)
    private int jobsFound;

    @Column(name = "jobs_ingested", nullable = false)
    private int jobsIngested;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "is_running", nullable = false)
    private boolean isRunning;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getPageOffset() { return pageOffset; }
    public void setPageOffset(int pageOffset) { this.pageOffset = pageOffset; }
    public Instant getLastCrawlStartedAt() { return lastCrawlStartedAt; }
    public void setLastCrawlStartedAt(Instant lastCrawlStartedAt) { this.lastCrawlStartedAt = lastCrawlStartedAt; }
    public Instant getLastCrawlFinishedAt() { return lastCrawlFinishedAt; }
    public void setLastCrawlFinishedAt(Instant lastCrawlFinishedAt) { this.lastCrawlFinishedAt = lastCrawlFinishedAt; }
    public int getJobsFound() { return jobsFound; }
    public void setJobsFound(int jobsFound) { this.jobsFound = jobsFound; }
    public int getJobsIngested() { return jobsIngested; }
    public void setJobsIngested(int jobsIngested) { this.jobsIngested = jobsIngested; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public boolean isRunning() { return isRunning; }
    public void setRunning(boolean running) { isRunning = running; }
    public Instant getUpdatedAt() { return updatedAt; }
}
