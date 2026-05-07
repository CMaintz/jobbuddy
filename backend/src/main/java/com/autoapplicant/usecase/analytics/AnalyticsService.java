package com.autoapplicant.usecase.analytics;

import com.autoapplicant.domain.analytics.ApplicationMetrics;
import com.autoapplicant.domain.analytics.DashboardData;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.port.in.analytics.*;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnalyticsService implements GetDashboardUseCase, GetApplicationMetricsUseCase {

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
