package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.job.JobResponse;
import com.autoapplicant.adapter.web.dto.job.ManualJobRequest;
import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.domain.search.JobSearchFilters;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.port.in.document.GetDocumentsForJobUseCase;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.in.job.ReportJobInactiveUseCase;
import com.autoapplicant.port.in.matching.SubmitRecommendationFeedbackUseCase;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs")
public class JobController {

    private final GetJobsUseCase getJobs;
    private final GetJobByIdUseCase getJobById;
    private final GetSavedJobsUseCase getSavedJobs;
    private final GetIgnoredJobsUseCase getIgnoredJobs;
    private final SearchJobsUseCase searchJobs;
    private final GetRecommendationsUseCase getRecommendations;
    private final SaveJobUseCase saveJob;
    private final IgnoreJobUseCase ignoreJob;
    private final CreateManualJobUseCase createManualJob;
    private final SubmitRecommendationFeedbackUseCase feedbackUseCase;
    private final GetDocumentsForJobUseCase getDocsForJob;
    private final ReportJobInactiveUseCase reportJobInactive;
    private final SecurityContextHelper secCtx;

    public JobController(GetJobsUseCase getJobs, GetJobByIdUseCase getJobById,
                         GetSavedJobsUseCase getSavedJobs, GetIgnoredJobsUseCase getIgnoredJobs,
                         SearchJobsUseCase searchJobs, GetRecommendationsUseCase getRecommendations,
                         SaveJobUseCase saveJob, IgnoreJobUseCase ignoreJob,
                         CreateManualJobUseCase createManualJob,
                         SubmitRecommendationFeedbackUseCase feedbackUseCase,
                         GetDocumentsForJobUseCase getDocsForJob,
                         ReportJobInactiveUseCase reportJobInactive,
                         SecurityContextHelper secCtx) {
        this.getJobs = getJobs;
        this.getJobById = getJobById;
        this.getSavedJobs = getSavedJobs;
        this.getIgnoredJobs = getIgnoredJobs;
        this.searchJobs = searchJobs;
        this.getRecommendations = getRecommendations;
        this.saveJob = saveJob;
        this.ignoreJob = ignoreJob;
        this.createManualJob = createManualJob;
        this.feedbackUseCase = feedbackUseCase;
        this.getDocsForJob = getDocsForJob;
        this.reportJobInactive = reportJobInactive;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List jobs")
    @GetMapping
    public ResponseEntity<Page<JobResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = secCtx.getCurrentUserId();
        JobSearchQuery query = new JobSearchQuery(null, null, page, size, "postedAt", userId);
        return ResponseEntity.ok(getJobs.getJobs(query).map(JobResponse::from));
    }

    @Operation(summary = "Search jobs by query")
    @GetMapping("/search")
    public ResponseEntity<JobSearchResult> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        JobSearchQuery query = new JobSearchQuery(q, new JobSearchFilters(null, null, null,
                null, null, null, null, null, null, null, categories), page, size, "postedAt");
        return ResponseEntity.ok(searchJobs.searchJobs(query));
    }

    @Operation(summary = "Get personalized job recommendations")
    @GetMapping("/recommendations")
    public ResponseEntity<List<MatchResult>> recommendations(
            @RequestParam(defaultValue = "10") int limit) {
        UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(getRecommendations.getRecommendations(userId, limit));
    }

    @Operation(summary = "Get job by id")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Job not found"))
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getById(@PathVariable UUID id) {
        return getJobById.getJobById(id)
                .map(job -> ResponseEntity.ok(JobResponse.from(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List saved jobs")
    @GetMapping("/saved")
    public ResponseEntity<List<JobResponse>> saved() {
        UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(getSavedJobs.getSavedJobs(userId).stream()
                .map(JobResponse::from).toList());
    }

    @Operation(summary = "Save a job")
    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Boolean>> save(@PathVariable UUID id) {
        saveJob.saveJob(secCtx.getCurrentUserId(), id);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @Operation(summary = "Unsave a job")
    @DeleteMapping("/{id}/save")
    public ResponseEntity<Void> unsave(@PathVariable UUID id) {
        saveJob.unsaveJob(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ignore a job")
    @PostMapping("/{id}/ignore")
    public ResponseEntity<Void> ignore(@PathVariable UUID id,
                                        @RequestParam(required = false) String reason) {
        ignoreJob.ignoreJob(secCtx.getCurrentUserId(), id, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Report a job as taken down — hides it for the user and verifies the URL server-side")
    @ApiResponse(responseCode = "202", description = "Report accepted; verification runs in the background")
    @PostMapping("/{id}/report-inactive")
    public ResponseEntity<Void> reportInactive(@PathVariable UUID id) {
        reportJobInactive.reportInactive(secCtx.getCurrentUserId(), id);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Unignore a job")
    @DeleteMapping("/{id}/ignore")
    public ResponseEntity<Void> unignore(@PathVariable UUID id) {
        getIgnoredJobs.unignoreJob(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List ignored jobs")
    @GetMapping("/ignored")
    public ResponseEntity<List<IgnoredJob>> ignored() {
        return ResponseEntity.ok(getIgnoredJobs.getIgnoredJobs(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "List generated documents for a job")
    @GetMapping("/{id}/documents")
    public ResponseEntity<List<GeneratedDocument>> jobDocuments(@PathVariable UUID id) {
        return ResponseEntity.ok(getDocsForJob.getDocumentsForJob(id));
    }

    @Operation(summary = "Submit recommendation feedback for a job")
    @PostMapping("/{id}/feedback")
    public ResponseEntity<RecommendationFeedback> feedback(@PathVariable UUID id,
                                                            @RequestParam FeedbackType type) {
        return ResponseEntity.ok(feedbackUseCase.submitFeedback(secCtx.getCurrentUserId(), id, type));
    }

    @Operation(summary = "Remove recommendation feedback for a job")
    @DeleteMapping("/{id}/feedback")
    public ResponseEntity<Void> removeFeedback(@PathVariable UUID id) {
        feedbackUseCase.removeFeedback(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Look up job by URL")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Job not found"))
    @GetMapping("/lookup")
    public ResponseEntity<JobResponse> lookupByUrl(@RequestParam String url) {
        return getJobById.lookupByUrl(url)
                .map(job -> ResponseEntity.ok(JobResponse.from(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Add job manually")
    @ApiResponse(responseCode = "201", description = "Job created")
    @PostMapping("/manual")
    public ResponseEntity<JobResponse> addManually(@Valid @RequestBody ManualJobRequest req) {
        Job job = new Job(null, JobSource.MANUAL, null, req.url(),
                req.title(), null, req.companyName(),
                req.description(), req.description(),
                req.employmentType() != null ? EmploymentType.valueOf(req.employmentType()) : null,
                null, req.remoteType() != null ? RemoteType.valueOf(req.remoteType()) : null,
                req.location(), null, null, null,
                req.salaryMin(), req.salaryMax(), req.currency() != null ? req.currency() : "DKK",
                List.of(), List.of(), List.of(),
                Instant.now(), Instant.now(), null, List.of(), null, null, true, null, null, null, null, Instant.now(), null);
        Job saved = createManualJob.createManualJob(job);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + saved.id())).body(JobResponse.from(saved));
    }
}
