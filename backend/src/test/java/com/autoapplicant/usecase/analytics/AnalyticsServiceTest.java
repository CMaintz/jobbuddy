package com.autoapplicant.usecase.analytics;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.analytics.DailyCount;
import com.autoapplicant.domain.analytics.DashboardData;
import com.autoapplicant.domain.analytics.DetailedMetrics;
import com.autoapplicant.domain.analytics.WeeklyTrend;
import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final double PERCENT_TOLERANCE = 0.01;

    private final ApplicationRepositoryPort applicationRepo = mock(ApplicationRepositoryPort.class);
    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final AnalyticsService service = new AnalyticsService(applicationRepo, jobRepo);

    @Test
    void detailed_metrics_count_every_status_past_saved_as_applied_and_rate_against_that() {
        givenApplications(
                applied(SAVED, daysAgo(1)),
                applied(APPLIED, daysAgo(2)),
                applied(APPLIED, daysAgo(20)),
                applied(RECRUITER_CONTACT, daysAgo(40)),
                applied(INTERVIEW, daysAgo(40)),
                applied(OFFER, daysAgo(40)),
                applied(REJECTED, daysAgo(40)));

        DetailedMetrics metrics = service.getDetailedMetrics(USER);

        assertThat(metrics.total()).isEqualTo(7);
        assertThat(metrics.saved()).isEqualTo(1);
        assertThat(metrics.applied()).isEqualTo(6);
        assertThat(metrics.pendingResponse()).isEqualTo(2);
        assertThat(metrics.activeInterviews()).isEqualTo(1);
        assertThat(metrics.offers()).isEqualTo(1);
        assertThat(metrics.appliedThisWeek()).isEqualTo(1);
        assertThat(metrics.appliedThisMonth()).isEqualTo(2);
        assertThat(metrics.responseRate()).isCloseTo(66.67, within(PERCENT_TOLERANCE));
        assertThat(metrics.interviewRate()).isCloseTo(16.67, within(PERCENT_TOLERANCE));
        assertThat(metrics.offerRate()).isCloseTo(100.0, within(PERCENT_TOLERANCE));
    }

    @Test
    void with_nothing_applied_the_rates_are_zero_rather_than_a_division_by_zero() {
        givenApplications(applied(SAVED, daysAgo(1)));

        DetailedMetrics metrics = service.getDetailedMetrics(USER);

        assertThat(metrics.responseRate()).isZero();
        assertThat(metrics.interviewRate()).isZero();
        assertThat(metrics.offerRate()).isZero();
    }

    @Test
    void top_companies_are_ranked_by_how_often_the_user_applied_to_them() {
        UUID acme = job("Acme");
        UUID globex = job("Globex");
        UUID unnamed = job(" ");
        givenApplications(
                forJob(globex), forJob(acme), forJob(acme), forJob(globex), forJob(acme), forJob(unnamed));

        assertThat(service.getDetailedMetrics(USER).topCompanies()).containsExactly("Acme", "Globex");
    }

    @Test
    void the_weekly_trend_falls_back_to_the_creation_date_and_ignores_saved_roles() {
        Application noAppliedAt = new Application(UUID.randomUUID(), USER, null, PREPARING, null,
                null, null, null, null, null, null, null, null, null, null,
                daysAgo(2), daysAgo(2), null, null);
        givenApplications(
                applied(APPLIED, daysAgo(1)),
                applied(APPLIED, daysAgo(1)),
                noAppliedAt,
                applied(SAVED, daysAgo(1)),
                applied(REJECTED, daysAgo(10)));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        WeeklyTrend trend = service.getWeeklyTrend(USER);

        assertThat(trend.thisWeek()).isEqualTo(3);
        assertThat(trend.lastWeek()).isEqualTo(1);
        assertThat(trend.delta()).isEqualTo(2);
        assertThat(trend.message()).isEqualTo("Better than last week! +2 applications");
        assertThat(trend.daily()).hasSize(14);
        assertThat(trend.daily().getFirst().date()).isEqualTo(today.minusDays(13));
        assertThat(trend.daily().getLast().date()).isEqualTo(today);
        Map<LocalDate, Integer> byDate = trend.daily().stream()
                .collect(Collectors.toMap(DailyCount::date, DailyCount::count));
        assertThat(byDate.get(today.minusDays(1))).isEqualTo(2);
        assertThat(byDate.get(today.minusDays(2))).isEqualTo(1);
        assertThat(byDate.get(today.minusDays(10))).isEqualTo(1);
    }

    @Test
    void the_weekly_message_tells_a_slowdown_from_a_week_that_has_not_started() {
        givenApplications(applied(APPLIED, daysAgo(10)));
        assertThat(service.getWeeklyTrend(USER).message()).isEqualTo("Fewer than last week. Keep at it!");

        givenApplications();
        assertThat(service.getWeeklyTrend(USER).message())
                .isEqualTo("No applications yet this week. Time to start!");

        givenApplications(applied(APPLIED, daysAgo(1)), applied(APPLIED, daysAgo(10)));
        assertThat(service.getWeeklyTrend(USER).message()).isEqualTo("Same pace as last week.");

        givenApplications(applied(APPLIED, daysAgo(1)));
        assertThat(service.getWeeklyTrend(USER).message()).isEqualTo("Better than last week! +1 application");
    }

    @Test
    void the_dashboard_sorts_applications_into_waiting_and_interviewing() {
        UUID savedJob = job("Acme");
        givenApplications(
                forJob(savedJob).toBuilder().status(SAVED).build(),
                applied(PREPARING, daysAgo(1)),
                applied(APPLIED, daysAgo(1)),
                applied(RECRUITER_CONTACT, daysAgo(1)),
                applied(TECHNICAL_TEST, daysAgo(1)),
                applied(FINAL_ROUND, daysAgo(1)),
                applied(ARCHIVED, daysAgo(1)));

        DashboardData dashboard = service.getDashboard(USER, List.of());

        assertThat(dashboard.savedJobs()).extracting(Job::companyName).containsExactly("Acme");
        assertThat(dashboard.pendingApplications()).extracting(Application::status)
                .containsExactlyInAnyOrder(PREPARING, APPLIED);
        assertThat(dashboard.upcomingInterviews()).extracting(Application::status)
                .containsExactlyInAnyOrder(TECHNICAL_TEST, FINAL_ROUND);
        assertThat(dashboard.activeApplications()).isEqualTo(4);
    }

    private void givenApplications(Application... applications) {
        when(applicationRepo.findByUserId(USER)).thenReturn(List.of(applications));
    }

    private UUID job(String companyName) {
        UUID id = UUID.randomUUID();
        when(jobRepo.findById(id)).thenReturn(Optional.of(
                Job.builder().id(id).title("Role").companyName(companyName).build()));
        return id;
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    private static Application applied(ApplicationStatus status, Instant appliedAt) {
        return new Application(UUID.randomUUID(), USER, null, status, appliedAt,
                null, null, null, null, null, null, null, null, null, null,
                appliedAt, appliedAt, null, null);
    }

    private static Application forJob(UUID jobId) {
        return applied(APPLIED, daysAgo(1)).toBuilder().jobId(jobId).build();
    }
}
