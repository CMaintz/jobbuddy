package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.ai.AnalyzeCvRequest;
import com.autoapplicant.adapter.web.dto.ai.GenerateDocumentRequest;
import com.autoapplicant.adapter.web.dto.ai.ParseCvRequest;
import com.autoapplicant.adapter.web.dto.ai.SaveStructuredDocumentRequest;
import com.autoapplicant.adapter.web.dto.ai.StructuredGenerateRequest;
import com.autoapplicant.domain.ai.*;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.StructuredDocumentService;
import com.autoapplicant.usecase.document.StructuredGeneratedDocumentService;
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
    private final CvVersionRepositoryPort cvRepo;
    private final JobRepositoryPort jobRepo;
    private final GeneratedDocumentRepositoryPort docRepo;
    private final StructuredDocumentService structuredDocuments;
    private final StructuredGeneratedDocumentService structuredGeneratedDocuments;
    private final GenerateDocumentUseCase generateDocument;
    private final SecurityContextHelper secCtx;

    public AiController(AnalyzeCvUseCase analyze,
                        ParseCvUseCase parseCv, RefineDocumentUseCase refine,
                        CvVersionRepositoryPort cvRepo, JobRepositoryPort jobRepo,
                        GeneratedDocumentRepositoryPort docRepo,
                        StructuredDocumentService structuredDocuments,
                        StructuredGeneratedDocumentService structuredGeneratedDocuments,
                        GenerateDocumentUseCase generateDocument,
                        SecurityContextHelper secCtx) {
        this.analyze = analyze;
        this.parseCv = parseCv;
        this.refine = refine;
        this.cvRepo = cvRepo;
        this.jobRepo = jobRepo;
        this.docRepo = docRepo;
        this.structuredDocuments = structuredDocuments;
        this.structuredGeneratedDocuments = structuredGeneratedDocuments;
        this.generateDocument = generateDocument;
        this.secCtx = secCtx;
    }

    public record RefineRequest(
            @jakarta.validation.constraints.NotBlank String currentContent,
            @jakarta.validation.constraints.NotBlank String userMessage,
            String jobDescription,
            String targetLanguage
    ) {}

    @Operation(summary = "Get CV render model")
    @GetMapping("/cv/render-model")
    public ResponseEntity<StructuredDocument> cvRenderModel(
            @RequestParam(required = false) String templateId) {
        return ResponseEntity.ok(structuredDocuments.buildCv(
                secCtx.getCurrentUserId(), templateId, false));
    }

    @Operation(summary = "Generate tailored CV")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "CV generation in progress"))
    @PostMapping("/cv/generate-structured")
    public DeferredResult<ResponseEntity<StructuredDocument>> generateStructuredCv(
            @Valid @RequestBody StructuredGenerateRequest req) {
        DeferredResult<ResponseEntity<StructuredDocument>> result = new DeferredResult<>(60_000L);
        UUID userId = secCtx.getCurrentUserId();
        CompletableFuture.supplyAsync(() -> structuredDocuments.generateTailoredCv(
                        userId,
                        req.jobId(),
                        req.jobDescription(),
                        req.customInstructions(),
                        req.targetLanguage(),
                        req.templateId(),
                        Boolean.TRUE.equals(req.showProfileImage()),
                        req.theme() != null ? req.theme().toTheme() : null))
                .thenAccept(r -> result.setResult(ResponseEntity.ok(
                        structuredGeneratedDocuments.save(userId, req.jobId(), r, "gpt-4o"))))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @Operation(summary = "Generate application document")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Document generation in progress"))
    @PostMapping("/generate-document")
    public DeferredResult<ResponseEntity<StructuredDocument>> generateDocument(
            @Valid @RequestBody GenerateDocumentRequest req) {
        DeferredResult<ResponseEntity<StructuredDocument>> result = new DeferredResult<>(60_000L);
        UUID userId = secCtx.getCurrentUserId();
        generateDocument.generateDocument(
                        userId,
                        req.documentType(),
                        req.jobId(),
                        req.jobDescription(),
                        req.templateId(),
                        req.customInstructions(),
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
        return ResponseEntity.ok(docRepo.findByUserId(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Save structured document")
    @PostMapping("/documents/structured")
    public ResponseEntity<StructuredDocument> saveStructuredDocument(
            @Valid @RequestBody SaveStructuredDocumentRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        return ResponseEntity.ok(structuredGeneratedDocuments.save(
                userId, req.jobId(), req.document(), "manual-edit"));
    }

    @Operation(summary = "Parse CV text into profile")
    @PostMapping("/parse-cv")
    public ResponseEntity<Profile> parseCv(@Valid @RequestBody ParseCvRequest req) {
        Profile parsed = parseCv.parseCvText(secCtx.getCurrentUserId(), req.rawCvText());
        return ResponseEntity.ok(parsed);
    }

    @Operation(summary = "Refine document with AI")
    @PostMapping("/refine")
    public DeferredResult<ResponseEntity<RefineDocumentResult>> refine(
            @Valid @RequestBody RefineRequest req) {
        DeferredResult<ResponseEntity<RefineDocumentResult>> result = new DeferredResult<>(60_000L);
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

    @Operation(summary = "Analyze CV against job description")
    @PostMapping("/analyze")
    public DeferredResult<ResponseEntity<AiAnalysisResult>> analyze(
            @Valid @RequestBody AnalyzeCvRequest req) {
        DeferredResult<ResponseEntity<AiAnalysisResult>> result = new DeferredResult<>(60_000L);
        String cvContent = cvRepo.findById(req.cvVersionId())
                .map(cv -> cv.content()).orElse("");
        String jobDesc = req.jobId() != null
                ? jobRepo.findById(req.jobId()).map(j -> j.descriptionClean()).orElse("") : "";
        AiAnalysisRequest request = new AiAnalysisRequest(cvContent, jobDesc, "FULL");
        analyze.analyze(request)
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }
}
