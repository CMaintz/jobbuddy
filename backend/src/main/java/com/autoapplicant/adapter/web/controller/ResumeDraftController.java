package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.ResumeDraft;
import com.autoapplicant.port.in.user.ManageResumeDraftUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resume-drafts")
@Tag(name = "Resume Drafts")
public class ResumeDraftController {

    private final ManageResumeDraftUseCase useCase;
    private final SecurityContextHelper secCtx;

    public ResumeDraftController(ManageResumeDraftUseCase useCase,
                                 SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List user resume drafts")
    @GetMapping
    public List<ResumeDraft> getDrafts() {
        return useCase.getDrafts(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Get a specific resume draft")
    @GetMapping("/{id}")
    public ResponseEntity<ResumeDraft> getDraft(@PathVariable UUID id) {
        return useCase.getDraft(secCtx.getCurrentUserId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get draft for a specific application")
    @GetMapping("/by-application/{applicationId}")
    public ResponseEntity<ResumeDraft> getDraftByApplication(@PathVariable UUID applicationId) {
        return useCase.getDraftByApplication(secCtx.getCurrentUserId(), applicationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a new resume draft")
    @ApiResponse(responseCode = "201", description = "Draft created")
    @PostMapping
    public ResponseEntity<ResumeDraft> createDraft(@RequestBody ResumeDraft draft) {
        ResumeDraft saved = useCase.createDraft(secCtx.getCurrentUserId(), draft);
        return ResponseEntity.created(URI.create("/api/v1/resume-drafts/" + saved.id())).body(saved);
    }

    @Operation(summary = "Save (auto-save) a resume draft")
    @PutMapping("/{id}")
    public ResumeDraft saveDraft(@PathVariable UUID id, @RequestBody ResumeDraft draft) {
        return useCase.saveDraft(secCtx.getCurrentUserId(), id, draft);
    }

    @Operation(summary = "Publish a resume draft")
    @PutMapping("/{id}/publish")
    public ResumeDraft publishDraft(@PathVariable UUID id) {
        return useCase.publishDraft(secCtx.getCurrentUserId(), id);
    }

    @Operation(summary = "Delete a resume draft")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable UUID id) {
        useCase.deleteDraft(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
