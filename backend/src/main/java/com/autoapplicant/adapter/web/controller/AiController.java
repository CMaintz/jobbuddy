package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.ai.AnalyzeCvRequest;
import com.autoapplicant.adapter.web.dto.ai.GenerateDocumentRequest;
import com.autoapplicant.adapter.web.dto.ai.GenerateRequest;
import com.autoapplicant.adapter.web.dto.ai.ParseCvRequest;
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
import com.autoapplicant.usecase.ai.AiService;
import com.autoapplicant.usecase.document.StructuredDocumentService;
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

    private final GenerateDocumentUseCase generate;
    private final AnalyzeCvUseCase analyze;
    private final ParseCvUseCase parseCv;
    private final RefineDocumentUseCase refine;
    private final CvVersionRepositoryPort cvRepo;
    private final JobRepositoryPort jobRepo;
    private final GeneratedDocumentRepositoryPort docRepo;
    private final StructuredDocumentService structuredDocuments;
    private final AiService aiService;
    private final SecurityContextHelper secCtx;

    public AiController(GenerateDocumentUseCase generate, AnalyzeCvUseCase analyze,
                        ParseCvUseCase parseCv, RefineDocumentUseCase refine,
                        CvVersionRepositoryPort cvRepo, JobRepositoryPort jobRepo,
                        GeneratedDocumentRepositoryPort docRepo,
                        StructuredDocumentService structuredDocuments,
                        AiService aiService,
                        SecurityContextHelper secCtx) {
        this.generate = generate;
        this.analyze = analyze;
        this.parseCv = parseCv;
        this.refine = refine;
        this.cvRepo = cvRepo;
        this.jobRepo = jobRepo;
        this.docRepo = docRepo;
        this.structuredDocuments = structuredDocuments;
        this.aiService = aiService;
        this.secCtx = secCtx;
    }

    public record RefineRequest(
            @jakarta.validation.constraints.NotBlank String currentContent,
            @jakarta.validation.constraints.NotBlank String userMessage,
            String jobDescription,
            String targetLanguage
    ) {}

    @PostMapping("/generate")
    public DeferredResult<ResponseEntity<AiGenerationResult>> generate(
            @Valid @RequestBody GenerateRequest req) {
        DeferredResult<ResponseEntity<AiGenerationResult>> result = new DeferredResult<>(60_000L);
        UUID userId = secCtx.getCurrentUserId();
        AiGenerationRequest request = new AiGenerationRequest(
                req.jobId(), req.jobDescription(), req.cvVersionId(), req.promptTemplateId(),
                userId, req.customInstructions(), req.documentType(), req.targetLanguage(),
                req.useStyleFromHistory());
        generate.generate(request)
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @GetMapping("/cv/render-model")
    public ResponseEntity<StructuredDocument> cvRenderModel(
            @RequestParam(required = false) String templateId) {
        return ResponseEntity.ok(structuredDocuments.buildCv(
                secCtx.getCurrentUserId(), templateId));
    }

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
                        req.templateId()))
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @PostMapping("/generate-document")
    public DeferredResult<ResponseEntity<StructuredDocument>> generateDocument(
            @RequestBody GenerateDocumentRequest req) {
        DeferredResult<ResponseEntity<StructuredDocument>> result = new DeferredResult<>(60_000L);
        UUID userId = secCtx.getCurrentUserId();
        aiService.generateDocument(
                        userId,
                        req.documentType(),
                        req.jobId(),
                        req.jobDescription(),
                        req.templateId(),
                        req.customInstructions(),
                        req.targetLanguage())
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
    }

    @GetMapping("/documents")
    public ResponseEntity<List<GeneratedDocument>> documents() {
        return ResponseEntity.ok(docRepo.findByUserId(secCtx.getCurrentUserId()));
    }

    @PostMapping("/parse-cv")
    public ResponseEntity<Profile> parseCv(@RequestBody ParseCvRequest req) {
        Profile parsed = parseCv.parseCvText(secCtx.getCurrentUserId(), req.rawCvText());
        return ResponseEntity.ok(parsed);
    }

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
