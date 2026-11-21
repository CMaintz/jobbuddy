package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.GetApplicationsUseCase;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Her applications with their status and dates, newest first. Recruiter names and e-mails and the
 * letters themselves stay out: they are other people's details and many tokens.
 */
@Component
@Profile("desktop")
public class ListApplicationsTool implements McpTool {

    static final int MAX_RESULTS = 25;

    private final GetApplicationsUseCase applications;
    private final GetJobByIdUseCase jobs;

    public ListApplicationsTool(GetApplicationsUseCase applications, GetJobByIdUseCase jobs) {
        this.applications = applications;
        this.jobs = jobs;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ApplicationSummary(UUID applicationId, UUID postingId, String title, String company,
                              String status, String appliedAt, String deadline,
                              String interviewAt) {}

    @Override
    public String name() {
        return "list_applications";
    }

    @Override
    public String description() {
        return "The user's applications, newest first, with status, the posting they are for and"
                + " their dates (applied, deadline, interview). Optionally only one status.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return McpSchema.object(Map.of("status", McpSchema.oneOf("Only this status", statuses())),
                List.of());
    }

    @Override
    public Object call(McpGrant grant, JsonNode args) {
        Optional<String> status = McpArgs.choice(args, "status", statuses());
        List<ApplicationSummary> found = applications.getApplications(grant.userId()).stream()
                .filter(a -> status.isEmpty() || status.get().equals(a.status().name()))
                .sorted(Comparator.comparing(Application::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RESULTS)
                .map(this::summarise)
                .toList();
        return Map.of("applications", found);
    }

    private ApplicationSummary summarise(Application a) {
        Optional<Job> job = a.jobId() == null ? Optional.empty() : jobs.getJobById(a.jobId());
        return new ApplicationSummary(a.id(), a.jobId(),
                job.map(Job::title).map(PostingSummary::cut).orElse(null),
                job.map(Job::companyName).map(PostingSummary::cut).orElse(null), a.status().name(),
                text(a.appliedAt()), text(a.deadline()), text(a.interviewAt()));
    }

    private static String text(Object value) {
        return Objects.toString(value, null);
    }

    private static List<String> statuses() {
        return Arrays.stream(ApplicationStatus.values()).map(Enum::name).toList();
    }
}
