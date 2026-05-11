package com.autoapplicant.usecase.analytics;

import com.autoapplicant.domain.analytics.ApplicationMetrics;
import com.autoapplicant.domain.analytics.DashboardData;
import com.autoapplicant.domain.analytics.DetailedMetrics;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.port.in.analytics.*;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnalyticsService implements GetDashboardUseCase, GetApplicationMetricsUseCase, GetDetailedMetricsUseCase {

    private final ApplicationRepositoryPort applicationRepo;
    private final JobRepositoryPort jobRepo;
    private final GetRecommendationsUseCase recommendationsUseCase;

    public AnalyticsService(ApplicationRepositoryPort applicationRepo, JobRepositoryPort jobRepo,
                             GetRecommendationsUseCase recommendationsUseCase) {
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.recommendationsUseCase = recommendationsUseCase;
    }

    @Override
    public DashboardData getDashboard(UUID userId) {
        List<Application> applications = applicationRepo.findByUserId(userId);

        List<MatchResult> recommendations = recommendationsUseCase.getRecommendations(userId, 5);

        List<Job> savedJobs = applications.stream()
                .filter(a -> a.status() == ApplicationStatus.SAVED)
                .flatMap(a -> jobRepo.findById(a.jobId()).stream())
                .collect(Collectors.toList());

        List<Application> pendingApplications = applications.stream()
                .filter(a -> a.status() == ApplicationStatus.APPLIED
                        || a.status() == ApplicationStatus.PREPARING)
                .collect(Collectors.toList());

        List<Application> upcomingInterviews = applications.stream()
                .filter(a -> a.status() == ApplicationStatus.INTERVIEW
                        || a.status() == ApplicationStatus.TECHNICAL_TEST
                        || a.status() == ApplicationStatus.FINAL_ROUND)
                .collect(Collectors.toList());

        ApplicationMetrics weeklyMetrics = computeWeeklyMetrics(userId, applications);

        return new DashboardData(recommendations, savedJobs, pendingApplications,
                upcomingInterviews, weeklyMetrics);
    }

    @Override
    public ApplicationMetrics getMetrics(UUID userId) {
        List<Application> applications = applicationRepo.findByUserId(userId);
        return computeWeeklyMetrics(userId, applications);
    }

    @Override
    public DetailedMetrics getDetailedMetrics(UUID userId) {
        List<Application> applications = applicationRepo.findByUserId(userId);

        int total = applications.size();
        int saved = (int) applications.stream().filter(a -> a.status() == ApplicationStatus.SAVED).count();
        int applied = (int) applications.stream().filter(a -> a.status() != ApplicationStatus.SAVED).count();

        int pendingResponse = (int) applications.stream()
                .filter(a -> a.status() == ApplicationStatus.APPLIED).count();

        int activeInterviews = (int) applications.stream()
                .filter(a -> a.status() == ApplicationStatus.INTERVIEW
                        || a.status() == ApplicationStatus.TECHNICAL_TEST
                        || a.status() == ApplicationStatus.FINAL_ROUND).count();

        int offers = (int) applications.stream()
                .filter(a -> a.status() == ApplicationStatus.OFFER).count();

        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        int appliedThisWeek = (int) applications.stream()
                .filter(a -> a.status() != ApplicationStatus.SAVED)
                .filter(a -> {
                    Instant ts = a.appliedAt() != null ? a.appliedAt() : a.createdAt();
                    return ts != null && ts.isAfter(sevenDaysAgo);
                }).count();

        int appliedThisMonth = (int) applications.stream()
                .filter(a -> a.status() != ApplicationStatus.SAVED)
                .filter(a -> {
                    Instant ts = a.appliedAt() != null ? a.appliedAt() : a.createdAt();
                    return ts != null && ts.isAfter(thirtyDaysAgo);
                }).count();

        double responseRate = applied > 0 ? (double) (applied - pendingResponse) / applied * 100 : 0;
        double interviewRate = applied > 0 ? (double) activeInterviews / applied * 100 : 0;
        double offerRate = activeInterviews > 0 ? (double) offers / activeInterviews * 100 : 0;

        // Top 5 companies by application count
        List<String> topCompanies = applications.stream()
                .filter(a -> a.jobId() != null)
                .map(a -> jobRepo.findById(a.jobId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get().companyName())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return new DetailedMetrics(total, saved, applied, pendingResponse, activeInterviews, offers,
                appliedThisWeek, appliedThisMonth, responseRate, interviewRate, offerRate, topCompanies);
    }

    private ApplicationMetrics computeWeeklyMetrics(UUID userId, List<Application> applications) {
        int total = applications.size();
        int saved = (int) applications.stream().filter(a -> a.status() == ApplicationStatus.SAVED).count();
        int applied = (int) applications.stream().filter(a -> a.status() != ApplicationStatus.SAVED).count();
        int interview = (int) applications.stream()
                .filter(a -> a.status() == ApplicationStatus.INTERVIEW
                        || a.status() == ApplicationStatus.TECHNICAL_TEST
                        || a.status() == ApplicationStatus.FINAL_ROUND).count();
        int offers = (int) applications.stream().filter(a -> a.status() == ApplicationStatus.OFFER).count();

        double responseRate = applied > 0 ? (double) interview / applied * 100 : 0;
        double interviewRate = applied > 0 ? (double) interview / applied * 100 : 0;
        double offerRate = interview > 0 ? (double) offers / interview * 100 : 0;

        return new ApplicationMetrics(null, userId,
                LocalDate.now().minusDays(7), LocalDate.now(),
                total, saved, 0, responseRate, interviewRate, offerRate);
    }
}
