package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.application.*;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.domain.application.ApplicationTimelineEntry;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.*;
import com.autoapplicant.port.in.job.GetJobByIdUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications")
public class ApplicationController {

    private final CreateApplicationUseCase create;
    private final UpdateApplicationStatusUseCase updateStatus;
    private final UpdateRecruiterInfoUseCase updateRecruiter;
    private final UpdateOutcomeUseCase updateOutcome;
    private final GetApplicationsUseCase getAll;
    private final GetApplicationByIdUseCase getById;
    private final GetJobByIdUseCase getJobById;
    private final GetApplicationTimelineUseCase timeline;
    private final SecurityContextHelper secCtx;

    public ApplicationController(CreateApplicationUseCase create,
                                  UpdateApplicationStatusUseCase updateStatus,
                                  UpdateRecruiterInfoUseCase updateRecruiter,
                                  UpdateOutcomeUseCase updateOutcome,
                                  GetApplicationsUseCase getAll,
                                  GetApplicationByIdUseCase getById,
                                  GetJobByIdUseCase getJobById,
                                  GetApplicationTimelineUseCase timeline,
                                  SecurityContextHelper secCtx) {
        this.create = create;
        this.updateStatus = updateStatus;
        this.updateRecruiter = updateRecruiter;
        this.updateOutcome = updateOutcome;
        this.getAll = getAll;
        this.getById = getById;
        this.getJobById = getJobById;
        this.timeline = timeline;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List applications")
    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> list(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = secCtx.getCurrentUserId();
        List<ApplicationResponse> result = getAll.getApplications(userId, pageable).getContent().stream()
                .map(a -> {
                    Job job = a.jobId() != null ? getJobById.getJobById(a.jobId()).orElse(null) : null;
                    return ApplicationResponse.from(a, job);
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Create application")
    @ApiResponse(responseCode = "201", description = "Application created")
    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        var app = create.createApplication(new CreateApplicationCommand(
                userId,
                req.jobId(),
                req.cvVersionId(),
                req.promptTemplateId(),
                req.generatedDocumentId(),
                parseStatus(req.status(), ApplicationStatus.SAVED),
                req.coverLetterText(),
                req.applicationText(),
                req.recruiterMessage(),
                req.matchScore(),
                req.notes()));
        return ResponseEntity.created(URI.create("/api/v1/applications/" + app.id())).body(ApplicationResponse.from(app));
    }

    @Operation(summary = "Attach generated document to application")
    @PostMapping("/{id}/generated-documents/{documentId}")
    public ResponseEntity<ApplicationResponse> attachGeneratedDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestBody(required = false) AttachGeneratedDocumentRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        var updated = create.attachGeneratedDocument(
                id,
                userId,
                documentId,
                req != null ? req.generatedContent() : null,
                req != null ? parseStatus(req.status(), null) : null,
                req != null ? req.notes() : null);
        return ResponseEntity.ok(ApplicationResponse.from(updated));
    }

    @Operation(summary = "Get application by id")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Application not found"))
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getOne(@PathVariable UUID id) {
        UUID userId = secCtx.getCurrentUserId();
        return getById.getApplicationById(id, userId)
                .map(a -> {
                    Job job = a.jobId() != null ? getJobById.getJobById(a.jobId()).orElse(null) : null;
                    return ResponseEntity.ok(ApplicationResponse.from(a, job));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update application status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        var updated = updateStatus.updateStatus(id, userId, req.status(), req.notes());
        return ResponseEntity.ok(ApplicationResponse.from(updated));
    }

    @Operation(summary = "Update recruiter contact details and reply")
    @PatchMapping("/{id}/recruiter")
    public ResponseEntity<ApplicationResponse> updateRecruiter(
            @PathVariable UUID id,
            @RequestBody UpdateRecruiterInfoRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        var updated = updateRecruiter.updateRecruiterInfo(id, userId,
                req.recruiterName(), req.recruiterEmail(),
                req.recruiterMessage(), req.recruiterReply());
        return ResponseEntity.ok(ApplicationResponse.from(updated));
    }

    @Operation(summary = "Record application outcome (feedback received, lessons for next time)")
    @PatchMapping("/{id}/outcome")
    public ResponseEntity<ApplicationResponse> updateOutcome(
            @PathVariable UUID id,
            @RequestBody UpdateOutcomeRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        var updated = updateOutcome.updateOutcome(id, userId, req.outcomeFeedback(), req.outcomeLessons());
        return ResponseEntity.ok(ApplicationResponse.from(updated));
    }

    @Operation(summary = "Get the application's progress timeline")
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<ApplicationTimelineEntry>> getTimeline(@PathVariable UUID id) {
        return ResponseEntity.ok(timeline.getTimeline(id, secCtx.getCurrentUserId()));
    }

    private static ApplicationStatus parseStatus(String value, ApplicationStatus fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return ApplicationStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
