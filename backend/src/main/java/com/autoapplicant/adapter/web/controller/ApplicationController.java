package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.application.*;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.CreateApplicationCommand;
import com.autoapplicant.domain.analytics.ResponseMetric;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.*;
import com.autoapplicant.port.out.analytics.ResponseMetricRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications")
public class ApplicationController {

    private final CreateApplicationUseCase create;
    private final UpdateApplicationStatusUseCase updateStatus;
    private final GetApplicationsUseCase getAll;
    private final GetApplicationByIdUseCase getById;
    private final JobRepositoryPort jobRepo;
    private final ResponseMetricRepositoryPort responseMetricRepo;
    private final SecurityContextHelper secCtx;

    public ApplicationController(CreateApplicationUseCase create,
                                  UpdateApplicationStatusUseCase updateStatus,
                                  GetApplicationsUseCase getAll,
                                  GetApplicationByIdUseCase getById,
                                  JobRepositoryPort jobRepo,
                                  ResponseMetricRepositoryPort responseMetricRepo,
                                  SecurityContextHelper secCtx) {
        this.create = create;
        this.updateStatus = updateStatus;
        this.getAll = getAll;
        this.getById = getById;
        this.jobRepo = jobRepo;
        this.responseMetricRepo = responseMetricRepo;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List applications")
    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> list(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = secCtx.getCurrentUserId();
        List<ApplicationResponse> result = getAll.getApplications(userId, pageable).getContent().stream()
                .map(a -> {
                    Job job = a.jobId() != null ? jobRepo.findById(a.jobId()).orElse(null) : null;
                    return ApplicationResponse.from(a, job);
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Create application")
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
        return ResponseEntity.ok(ApplicationResponse.from(app));
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
                    Job job = a.jobId() != null ? jobRepo.findById(a.jobId()).orElse(null) : null;
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

    @Operation(summary = "Get application response timeline")
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<ResponseMetric>> timeline(@PathVariable UUID id) {
        return ResponseEntity.ok(responseMetricRepo.findByApplicationId(id));
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
