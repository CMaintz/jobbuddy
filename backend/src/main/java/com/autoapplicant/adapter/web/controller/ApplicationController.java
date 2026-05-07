package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.application.*;
import com.autoapplicant.port.in.application.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final SecurityContextHelper secCtx;

    public ApplicationController(CreateApplicationUseCase create,
                                  UpdateApplicationStatusUseCase updateStatus,
                                  GetApplicationsUseCase getAll,
                                  GetApplicationByIdUseCase getById,
                                  SecurityContextHelper secCtx) {
        this.create = create;
        this.updateStatus = updateStatus;
        this.getAll = getAll;
        this.getById = getById;
        this.secCtx = secCtx;
    }

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> list() {
        UUID userId = secCtx.getCurrentUserId();
        List<ApplicationResponse> result = getAll.getApplications(userId).stream()
                .map(ApplicationResponse::from).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        var app = create.createApplication(userId, req.jobId(), req.cvVersionId(), req.notes());
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getOne(@PathVariable UUID id) {
        UUID userId = secCtx.getCurrentUserId();
        return getById.getApplicationById(id, userId)
                .map(a -> ResponseEntity.ok(ApplicationResponse.from(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        var updated = updateStatus.updateStatus(id, userId, req.status(), req.notes());
        return ResponseEntity.ok(ApplicationResponse.from(updated));
    }
}
