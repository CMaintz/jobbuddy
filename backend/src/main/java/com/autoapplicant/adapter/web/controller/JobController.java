package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.job.JobResponse;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.domain.search.JobSearchFilters;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.job.IgnoredJob;
import com.autoapplicant.port.in.job.*;
import com.autoapplicant.port.in.matching.SubmitRecommendationFeedbackUseCase;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final SubmitRecommendationFeedbackUseCase feedbackUseCase;
    private final GeneratedDocumentRepositoryPort docRepo;
    private final SecurityContextHelper secCtx;

    public JobController(GetJobsUseCase getJobs, GetJobByIdUseCase getJobById,
                         GetSavedJobsUseCase getSavedJobs, GetIgnoredJobsUseCase getIgnoredJobs,
                         SearchJobsUseCase searchJobs, GetRecommendationsUseCase getRecommendations,
                         SaveJobUseCase saveJob, IgnoreJobUseCase ignoreJob,
                         SubmitRecommendationFeedbackUseCase feedbackUseCase,
                         GeneratedDocumentRepositoryPort docRepo,
                         SecurityContextHelper secCtx) {
        this.getJobs = getJobs;
        this.getJobById = getJobById;
        this.getSavedJobs = getSavedJobs;
        this.getIgnoredJobs = getIgnoredJobs;
        this.searchJobs = searchJobs;
        this.getRecommendations = getRecommendations;
        this.saveJob = saveJob;
        this.ignoreJob = ignoreJob;
        this.feedbackUseCase = feedbackUseCase;
        this.docRepo = docRepo;
        this.secCtx = secCtx;
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = secCtx.getCurrentUserId();
        JobSearchQuery query = new JobSearchQuery(null, null, page, size, "postedAt", userId);
        return ResponseEntity.ok(getJobs.getJobs(query).map(JobResponse::from));
    }

    @GetMapping("/search")
    public ResponseEntity<JobSearchResult> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        JobSearchQuery query = new JobSearchQuery(q, new JobSearchFilters(null, null, null,
                null, null, null, null, null, null, null), page, size, "postedAt");
        return ResponseEntity.ok(searchJobs.searchJobs(query));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<MatchResult>> recommendations(
            @RequestParam(defaultValue = "10") int limit) {
        UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(getRecommendations.getRecommendations(userId, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getById(@PathVariable UUID id) {
        return getJobById.getJobById(id)
                .map(job -> ResponseEntity.ok(JobResponse.from(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/saved")
    public ResponseEntity<List<JobResponse>> saved() {
        UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(getSavedJobs.getSavedJobs(userId).stream()
                .map(JobResponse::from).toList());
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Boolean>> save(@PathVariable UUID id) {
        saveJob.saveJob(secCtx.getCurrentUserId(), id);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @DeleteMapping("/{id}/save")
    public ResponseEntity<Void> unsave(@PathVariable UUID id) {
        saveJob.unsaveJob(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<Void> ignore(@PathVariable UUID id,
                                        @RequestParam(required = false) String reason) {
        ignoreJob.ignoreJob(secCtx.getCurrentUserId(), id, reason);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/ignore")
    public ResponseEntity<Void> unignore(@PathVariable UUID id) {
        getIgnoredJobs.unignoreJob(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ignored")
    public ResponseEntity<List<IgnoredJob>> ignored() {
        return ResponseEntity.ok(getIgnoredJobs.getIgnoredJobs(secCtx.getCurrentUserId()));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<List<GeneratedDocument>> jobDocuments(@PathVariable UUID id) {
        return ResponseEntity.ok(docRepo.findByJobId(id));
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<RecommendationFeedback> feedback(@PathVariable UUID id,
                                                            @RequestParam FeedbackType type) {
        return ResponseEntity.ok(feedbackUseCase.submitFeedback(secCtx.getCurrentUserId(), id, type));
    }

    @DeleteMapping("/{id}/feedback")
    public ResponseEntity<Void> removeFeedback(@PathVariable UUID id) {
        feedbackUseCase.removeFeedback(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
