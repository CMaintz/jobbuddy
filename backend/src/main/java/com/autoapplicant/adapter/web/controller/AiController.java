package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.ai.AnalyzeCvRequest;
import com.autoapplicant.adapter.web.dto.ai.GenerateDocumentRequest;
import com.autoapplicant.adapter.web.dto.ai.ParseCvRequest;
import com.autoapplicant.adapter.web.dto.ai.RefineRequest;
import com.autoapplicant.adapter.web.dto.ai.ReviewRequest;
import com.autoapplicant.adapter.web.dto.ai.SaveStructuredDocumentRequest;
import com.autoapplicant.adapter.web.dto.ai.StructuredGenerateRequest;
import com.autoapplicant.domain.ai.AiAnalysisResult;
import com.autoapplicant.domain.ai.AiUsageSummary;
import com.autoapplicant.domain.ai.RefineDocumentRequest;
import com.autoapplicant.domain.ai.RefineDocumentResult;
import com.autoapplicant.domain.ai.ReviewDocumentRequest;
import com.autoapplicant.domain.ai.ReviewDocumentResult;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.GetAiUsageUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.in.ai.ReviewDocumentUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.GetCvRenderModelUseCase;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.in.document.PersistGeneratedDocumentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI")
public class AiController {

    private final AnalyzeCvUseCase analyze;
    private final ParseCvUseCase parseCv;
    private final RefineDocumentUseCase refine;
    private final ReviewDocumentUseCase reviewDocument;
    private final GenerateDocumentUseCase generateDocument;
    private final GetCvRenderModelUseCase cvRenderModel;
    private final GenerateTailoredCvUseCase generateTailoredCv;
    private final PersistGeneratedDocumentUseCase persistedDocuments;
    private final GetAiUsageUseCase aiUsage;
    private final SecurityContextHelper secCtx;

    public AiController(AnalyzeCvUseCase analyze,
                        ParseCvUseCase parseCv,
                        RefineDocumentUseCase refine,
                        ReviewDocumentUseCase reviewDocument,
                        GenerateDocumentUseCase generateDocument,
                        GetCvRenderModelUseCase cvRenderModel,
                        GenerateTailoredCvUseCase generateTailoredCv,
                        PersistGeneratedDocumentUseCase persistedDocuments,
                        GetAiUsageUseCase aiUsage,
                        SecurityContextHelper secCtx) {
        this.analyze = analyze;
        this.parseCv = parseCv;
        this.refine = refine;
        this.reviewDocument = reviewDocument;
        this.generateDocument = generateDocument;
        this.cvRenderModel = cvRenderModel;
        this.generateTailoredCv = generateTailoredCv;
        this.persistedDocuments = persistedDocuments;
        this.aiUsage = aiUsage;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get AI usage summary for current user")
    @GetMapping("/usage")
    public ResponseEntity<AiUsageSummary> getUsage() {
        return ResponseEntity.ok(aiUsage.getUsageSummary(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Get CV render model (non-tailored)")
    @GetMapping("/cv/render-model")
    public ResponseEntity<StructuredDocument> cvRenderModel(
            @RequestParam(required = false) String templateId) {
        return ResponseEntity.ok(cvRenderModel.buildCv(
                secCtx.getCurrentUserId(), templateId, false, null));
    }

    @Operation(summary = "Generate tailored CV")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "CV generation in progress"))
    @PostMapping("/cv/generate-structured")
    public DeferredResult<ResponseEntity<StructuredDocument>> generateStructuredCv(
            @Valid @RequestBody StructuredGenerateRequest req) {
        DeferredResult<ResponseEntity<StructuredDocument>> result = new DeferredResult<>(60_000L);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(504).body("AI generation timed out. Please try again.")));
        UUID userId = secCtx.getCurrentUserId();
        CompletableFuture.supplyAsync(() -> generateTailoredCv.generateTailoredCv(
                        userId,
                        req.jobId(),
                        req.jobDescription(),
                        req.customInstructions(),
                        req.targetLanguage(),
                        req.templateId(),
                        req.promptTemplateId(),
                        Boolean.TRUE.equals(req.showProfileImage()),
                        req.theme() != null ? req.theme().toTheme() : null))
                .thenAccept(r -> result.setResult(ResponseEntity.ok(
                        persistedDocuments.save(userId, req.jobId(), r, null))))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @Operation(summary = "Generate application document (cover letter, recruiter message, etc.)")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Document generation in progress"))
    @PostMapping("/generate-document")
    public DeferredResult<ResponseEntity<StructuredDocument>> generateDocument(
            @Valid @RequestBody GenerateDocumentRequest req) {
        DeferredResult<ResponseEntity<StructuredDocument>> result = new DeferredResult<>(60_000L);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(504).body("AI generation timed out. Please try again.")));
        UUID userId = secCtx.getCurrentUserId();
        generateDocument.generateDocument(
                        userId,
                        req.documentType(),
                        req.jobId(),
                        req.jobDescription(),
                        req.templateId(),
                        req.promptTemplateId(),
                        req.customInstructions(),
                        req.motivationText(),
                        req.targetLanguage(),
                        Boolean.TRUE.equals(req.showProfileImage()),
                        req.theme() != null ? req.theme().toTheme() : null)
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @Operation(summary = "List generated documents")
    @GetMapping("/documents")
    public ResponseEntity<List<GeneratedDocument>> documents() {
        return ResponseEntity.ok(persistedDocuments.listByUserId(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Save structured document")
    @PostMapping("/documents/structured")
    public ResponseEntity<StructuredDocument> saveStructuredDocument(
            @Valid @RequestBody SaveStructuredDocumentRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(persistedDocuments.save(
                userId, req.jobId(), req.document(), "manual-edit"));
    }

    @Operation(summary = "Parse CV text into profile")
    @PostMapping("/parse-cv")
    public ResponseEntity<Profile> parseCv(@Valid @RequestBody ParseCvRequest req) {
        return ResponseEntity.ok(parseCv.parseCvText(secCtx.getCurrentUserId(), req.rawCvText()));
    }

    @Operation(summary = "Refine document with AI")
    @PostMapping("/refine")
    public DeferredResult<ResponseEntity<RefineDocumentResult>> refine(
            @Valid @RequestBody RefineRequest req) {
        DeferredResult<ResponseEntity<RefineDocumentResult>> result = new DeferredResult<>(60_000L);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(504).body("AI refinement timed out. Please try again.")));
        UUID userId = secCtx.getCurrentUserId();
        RefineDocumentRequest request = new RefineDocumentRequest(
                userId, req.currentContent(), req.userMessage(),
                req.jobDescription(), req.targetLanguage());
        refine.refine(request)
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @Operation(summary = "Review a drafted document with a fresh-context AI reviewer")
    @PostMapping("/review")
    public DeferredResult<ResponseEntity<ReviewDocumentResult>> review(
            @Valid @RequestBody ReviewRequest req) {
        DeferredResult<ResponseEntity<ReviewDocumentResult>> result = new DeferredResult<>(60_000L);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(504).body("AI review timed out. Please try again.")));
        ReviewDocumentRequest request = new ReviewDocumentRequest(
                secCtx.getCurrentUserId(), req.currentContent(), req.documentType(),
                req.jobDescription(), req.targetLanguage());
        reviewDocument.review(request)
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @Operation(summary = "Analyze the master CV (or a CV version) against an optional job")
    @PostMapping("/analyze")
    public DeferredResult<ResponseEntity<AiAnalysisResult>> analyze(
            @Valid @RequestBody AnalyzeCvRequest req) {
        DeferredResult<ResponseEntity<AiAnalysisResult>> result = new DeferredResult<>(60_000L);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(504).body("AI analysis timed out. Please try again.")));
        analyze.analyze(secCtx.getCurrentUserId(), req.cvVersionId(), req.jobId(), req.jobDescription())
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }
}
