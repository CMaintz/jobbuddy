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

        int appliedThisWeek = countAppliedSince(applications, Instant.now().minus(7, ChronoUnit.DAYS));

        int activeApplications = (int) applications.stream()
                .filter(a -> a.status() == ApplicationStatus.APPLIED
                        || a.status() == ApplicationStatus.RECRUITER_CONTACT
                        || a.status() == ApplicationStatus.INTERVIEW
                        || a.status() == ApplicationStatus.TECHNICAL_TEST
                        || a.status() == ApplicationStatus.FINAL_ROUND)
                .count();

        return new DashboardData(recommendations, savedJobs, pendingApplications,
                upcomingInterviews, weeklyMetrics, appliedThisWeek, activeApplications);
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
        int saved = countByStatus(applications, ApplicationStatus.SAVED);
        int applied = total - saved;

        int pendingResponse = countByStatus(applications, ApplicationStatus.APPLIED);
        int activeInterviews = countByStatuses(applications,
                ApplicationStatus.INTERVIEW, ApplicationStatus.TECHNICAL_TEST, ApplicationStatus.FINAL_ROUND);
        int offers = countByStatus(applications, ApplicationStatus.OFFER);

        Instant now = Instant.now();
        int appliedThisWeek = countAppliedSince(applications, now.minus(7, ChronoUnit.DAYS));
        int appliedThisMonth = countAppliedSince(applications, now.minus(30, ChronoUnit.DAYS));

        double responseRate = applied > 0 ? (double) (applied - pendingResponse) / applied * 100 : 0;
        double interviewRate = applied > 0 ? (double) activeInterviews / applied * 100 : 0;
        double offerRate = activeInterviews > 0 ? (double) offers / activeInterviews * 100 : 0;

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
        int saved = countByStatus(applications, ApplicationStatus.SAVED);
        int applied = total - saved;
        int pendingResponse = countByStatus(applications, ApplicationStatus.APPLIED);
        int interview = countByStatuses(applications,
                ApplicationStatus.INTERVIEW, ApplicationStatus.TECHNICAL_TEST, ApplicationStatus.FINAL_ROUND);
        int offers = countByStatus(applications, ApplicationStatus.OFFER);

        double responseRate = applied > 0 ? (double) (applied - pendingResponse) / applied * 100 : 0;
        double interviewRate = applied > 0 ? (double) interview / applied * 100 : 0;
        double offerRate = interview > 0 ? (double) offers / interview * 100 : 0;

        return new ApplicationMetrics(null, userId,
                LocalDate.now().minusDays(7), LocalDate.now(),
                total, saved, 0, responseRate, interviewRate, offerRate);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static int countByStatus(List<Application> applications, ApplicationStatus status) {
        return (int) applications.stream().filter(a -> a.status() == status).count();
    }

    private static int countByStatuses(List<Application> applications, ApplicationStatus... statuses) {
        var set = java.util.Set.of(statuses);
        return (int) applications.stream().filter(a -> set.contains(a.status())).count();
    }

    private static int countAppliedSince(List<Application> applications, Instant since) {
        return (int) applications.stream()
                .filter(a -> a.status() != ApplicationStatus.SAVED)
                .filter(a -> {
                    Instant ts = a.appliedAt() != null ? a.appliedAt() : a.createdAt();
                    return ts != null && ts.isAfter(since);
                }).count();
    }
}
