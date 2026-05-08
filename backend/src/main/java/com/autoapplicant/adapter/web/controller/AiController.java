package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.ai.AnalyzeCvRequest;
import com.autoapplicant.adapter.web.dto.ai.GenerateRequest;
import com.autoapplicant.adapter.web.dto.ai.ParseCvRequest;
import com.autoapplicant.domain.ai.*;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.in.document.ParseCvUseCase;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

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
    private final SecurityContextHelper secCtx;

    public AiController(GenerateDocumentUseCase generate, AnalyzeCvUseCase analyze,
                        ParseCvUseCase parseCv, RefineDocumentUseCase refine,
                        CvVersionRepositoryPort cvRepo, JobRepositoryPort jobRepo,
                        SecurityContextHelper secCtx) {
        this.generate = generate;
        this.analyze = analyze;
        this.parseCv = parseCv;
        this.refine = refine;
        this.cvRepo = cvRepo;
        this.jobRepo = jobRepo;
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
                req.jobId(), req.cvVersionId(), req.promptTemplateId(),
                userId, req.customInstructions(), req.documentType(), req.targetLanguage());
        generate.generate(request)
                .thenAccept(r -> result.setResult(ResponseEntity.ok(r)))
                .exceptionally(e -> {
                    result.setErrorResult(e);
                    return null;
                });
        return result;
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
