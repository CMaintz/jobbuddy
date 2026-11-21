package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.GetApplicationsUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Her coming deadlines and interviews, soonest first. A deadline is her own date for the
 * application when she set one, else the posting's. Closed applications have none.
 */
@Component
@Profile("desktop")
public class UpcomingDatesTool implements McpTool {

    static final int MAX_RESULTS = 25;
    private static final int DEFAULT_DAYS = 14;
    private static final int MAX_DAYS = 60;
    private static final Set<ApplicationStatus> CLOSED =
            Set.of(ApplicationStatus.REJECTED, ApplicationStatus.ARCHIVED);

    private final GetApplicationsUseCase applications;
    private final GetJobByIdUseCase jobs;
    private final Clock clock;

    public UpcomingDatesTool(GetApplicationsUseCase applications, GetJobByIdUseCase jobs,
                             Clock clock) {
        this.applications = applications;
        this.jobs = jobs;
        this.clock = clock;
    }

    enum Kind { DEADLINE, INTERVIEW }

    /** {@code at} is a date for a deadline and a wall-clock date and time for an interview. */
    record UpcomingDate(Kind kind, String at, UUID applicationId, String title, String company,
                        @JsonIgnore LocalDate day) {}

    @Override
    public String name() {
        return "upcoming_dates";
    }

    @Override
    public String description() {
        return "The user's coming application deadlines and interviews, soonest first, at most "
                + MAX_RESULTS + ".";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of("days", McpSchema.integer("How many days ahead", 1, MAX_DAYS)),
                List.of());
    }

    @Override
    public Object call(McpGrant grant, JsonNode args) {
        LocalDate today = LocalDate.now(clock);
        LocalDate last = today.plusDays(McpArgs.number(args, "days", DEFAULT_DAYS, 1, MAX_DAYS));
        List<UpcomingDate> dates = applications.getApplications(grant.userId()).stream()
                .filter(a -> !CLOSED.contains(a.status()))
                .flatMap(a -> datesOf(a, a.jobId() == null ? Optional.empty() : jobs.getJobById(a.jobId())))
                .filter(d -> !d.day().isBefore(today) && !d.day().isAfter(last))
                .sorted(Comparator.comparing(UpcomingDate::at))
                .limit(MAX_RESULTS)
                .toList();
        return Map.of("dates", dates);
    }

    private static Stream<UpcomingDate> datesOf(Application a, Optional<Job> job) {
        LocalDate deadline = a.deadline() != null
                ? a.deadline() : job.map(Job::applicationDeadline).orElse(null);
        LocalDateTime interview = a.interviewAt();
        String title = job.map(Job::title).map(PostingSummary::cut).orElse(null);
        String company = job.map(Job::companyName).map(PostingSummary::cut).orElse(null);
        return Stream.of(
                deadline == null ? null : new UpcomingDate(Kind.DEADLINE, deadline.toString(),
                        a.id(), title, company, deadline),
                interview == null ? null : new UpcomingDate(Kind.INTERVIEW, interview.toString(),
                        a.id(), title, company, interview.toLocalDate()))
                .filter(Objects::nonNull);
    }
}
