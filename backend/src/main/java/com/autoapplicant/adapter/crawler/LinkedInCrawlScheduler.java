package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.in.linkedin.GenerateLinkedInQueryPlanUseCase;
import com.autoapplicant.port.out.crawler.CrawlOrchestrationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Drives the LinkedIn connector on its own low-volume, jittered cadence — deliberately
 * separate from the shared 4-hour crawl fan-out. Each firing waits a random delay
 * (so runs never land on a rigid tick), refreshes any stale/missing query plans, then
 * triggers a single per-source LinkedIn crawl.
 *
 * <p>The cron fires a few times a day at most; the connector itself caps how many
 * searches and detail fetches happen per run. See {@code LinkedInConnector}.
 */
@Component
public class LinkedInCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(LinkedInCrawlScheduler.class);

    private final GenerateLinkedInQueryPlanUseCase queryPlanner;
    private final CrawlOrchestrationPort orchestration;
    private final Executor crawlerTaskExecutor;

    @Value("${app.linkedin.enabled:true}")
    private boolean enabled;

    /** Upper bound on the random pre-run delay, in minutes, so runs don't fire on a fixed tick. */
    @Value("${app.linkedin.schedule.jitter-max-minutes:50}")
    private int jitterMaxMinutes;

    public LinkedInCrawlScheduler(GenerateLinkedInQueryPlanUseCase queryPlanner,
                                  CrawlOrchestrationPort orchestration,
                                  Executor crawlerTaskExecutor) {
        this.queryPlanner = queryPlanner;
        this.orchestration = orchestration;
        this.crawlerTaskExecutor = crawlerTaskExecutor;
    }

    @Scheduled(cron = "${app.linkedin.schedule.cron:0 0 9,13,18 * * *}")
    public void scheduledRun() {
        if (!enabled) {
            log.debug("LinkedIn scheduler disabled — skipping");
            return;
        }
        // Offload so the random pre-run delay never blocks Spring's scheduler thread.
        crawlerTaskExecutor.execute(this::runWithJitter);
    }

    private void runWithJitter() {
        try {
            long jitterMs = (long) (Math.random() * Math.max(0, jitterMaxMinutes) * 60_000L);
            log.info("LinkedIn scheduled run: waiting {} min of jitter before starting", jitterMs / 60_000);
            Thread.sleep(jitterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            queryPlanner.ensureFreshPlans();
        } catch (Exception e) {
            log.warn("LinkedIn: plan refresh failed, crawling with existing plans: {}", e.getMessage());
        }
        orchestration.runSource(JobSource.LINKEDIN);
    }
}
