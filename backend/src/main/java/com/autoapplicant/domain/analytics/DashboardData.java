package com.autoapplicant.domain.analytics;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchResult;

import java.util.List;

public record DashboardData(
        List<MatchResult> recommendedJobs,
        List<Job> savedJobs,
        List<Application> pendingApplications,
        List<Application> upcomingInterviews,
        ApplicationMetrics weeklyMetrics
) {}
