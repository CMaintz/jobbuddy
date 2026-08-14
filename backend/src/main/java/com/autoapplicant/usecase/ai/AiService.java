package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.*;
import com.autoapplicant.domain.document.*;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.in.ai.ReviewDocumentUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.BuildApplicationDocumentPort;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import com.autoapplicant.port.out.document.PersistGeneratedDocumentPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AiService implements AnalyzeCvUseCase, RefineDocumentUseCase, ReviewDocumentUseCase, GenerateDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiProviderPort aiProvider;
    private final JobRepositoryPort jobRepo;
    private final CvVersionRepositoryPort cvRepo;
    private final PromptTemplateRepositoryPort promptTemplateRepo;
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final PromptCompositionBuilder compositionBuilder;
    private final CareerProfileContextService careerProfileContext;
    private final BuildApplicationDocumentPort buildApplicationDocument;
    private final PersistGeneratedDocumentPort persistGeneratedDocument;
    private final ApplicationRepositoryPort applicationRepo;
    private final DocumentFactGuard factGuard;
    private final ObjectMapper objectMapper;

    /** When true, generateDocument runs a reviewer critique/revise pass on the draft before assembling. */
    @org.springframework.beans.factory.annotation.Value("${app.ai.auto-review.enabled:true}")
    private boolean autoReviewEnabled;

    /** Max reviewer passes; the loop also stops early once a pass reports no further critique. */
    @org.springframework.beans.factory.annotation.Value("${app.ai.auto-review.max-iterations:1}")
    private int autoReviewMaxIterations;

    /** When true, a deterministic fact gate checks generated metrics against the source profile. */
    @org.springframework.beans.factory.annotation.Value("${app.ai.fact-guard.enabled:true}")
    private boolean factGuardEnabled;

    /** {@code warn} logs unsupported metrics; {@code block} fails generation when any are found. */
    @org.springframework.beans.factory.annotation.Value("${app.ai.fact-guard.mode:warn}")
    private String factGuardMode;

    public AiService(@Qualifier("generationAiProvider") AiProviderPort aiProvider,
                     JobRepositoryPort jobRepo,
                     CvVersionRepositoryPort cvRepo,
                     PromptTemplateRepositoryPort promptTemplateRepo,
                     WritingProfileRepositoryPort writingProfileRepo,
                     PromptCompositionBuilder compositionBuilder,
                     CareerProfileContextService careerProfileContext,
                     BuildApplicationDocumentPort buildApplicationDocument,
                     PersistGeneratedDocumentPort persistGeneratedDocument,
                     ApplicationRepositoryPort applicationRepo,
                     DocumentFactGuard factGuard,
                     ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.jobRepo = jobRepo;
        this.cvRepo = cvRepo;
        this.promptTemplateRepo = promptTemplateRepo;
        this.writingProfileRepo = writingProfileRepo;
        this.compositionBuilder = compositionBuilder;
        this.careerProfileContext = careerProfileContext;
        this.buildApplicationDocument = buildApplicationDocument;
        this.persistGeneratedDocument = persistGeneratedDocument;
        this.applicationRepo = applicationRepo;
        this.factGuard = factGuard;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<AiAnalysisResult> analyze(UUID userId, UUID cvVersionId,
                                                       UUID jobId, String rawJobDescription) {
        try {
            // Explicit CV version wins; otherwise the PII-free master profile JSON.
            String cvContent = cvVersionId != null
                    ? cvRepo.findById(cvVersionId).map(CvVersion::content).orElse("")
                    : careerProfileContext.buildJson(userId);
            String jobDesc = jobId != null
                    ? jobRepo.findById(jobId).map(Job::descriptionClean).orElse(rawJobDescription)
                    : rawJobDescription;
            String prompt = buildAnalysisPrompt(cvContent, jobDesc);
            PromptComposition composition = new PromptComposition(
                    "You are an expert ATS reviewer and career coach. Analyze CVs and respond with JSON only. "
                    + "Never assume skills or experience the CV does not state.\n\n"
                    + PromptCompositionBuilder.UNTRUSTED_JOB_INPUT,
                    prompt, "", "", "", "", prompt);
            String response = sanitizeAiText(aiProvider.generateJson(composition));
            return CompletableFuture.completedFuture(parseAnalysis(response));
        } catch (Exception e) {
            log.error("CV analysis failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Parses the structured analysis; falls back to the raw text when the JSON is broken. */
    private AiAnalysisResult parseAnalysis(String response) {
        try {
            var node = objectMapper.readTree(AiResponseParser.extractJsonObject(response));
            List<String> suggestions = new java.util.ArrayList<>();
            node.path("suggestions").forEach(s -> suggestions.add(s.asText()));
            List<String> strengths = new java.util.ArrayList<>();
            node.path("strengths").forEach(s -> strengths.add(s.asText()));
            List<String> gaps = new java.util.ArrayList<>();
            node.path("gaps").forEach(s -> gaps.add(s.asText()));

            // Dimensional scores (job-targeted analyses). When complete, the overall
            // score is computed server-side from the fixed weights, not trusted from the model.
            AnalysisDimensions dimensions = parseDimensions(node.path("dimensions"));
            int score = dimensions != null && dimensions.isComplete()
                    ? dimensions.weightedScore()
                    : Math.max(0, Math.min(100, node.path("score").asInt(0)));

            return new AiAnalysisResult(suggestions, score, response,
                    node.path("summary").asText(null),
                    strengths, gaps, dimensions);
        } catch (Exception e) {
            log.warn("Analysis response was not valid JSON — returning raw text: {}", e.getMessage());
            return AiAnalysisResult.unstructured(response);
        }
    }

    private static AnalysisDimensions parseDimensions(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isObject()) return null;
        return new AnalysisDimensions(
                intOrNull(node, "technicalSkills"),
                intOrNull(node, "experience"),
                intOrNull(node, "cultureFit"),
                intOrNull(node, "careerAlignment"),
                node.path("location").asText(null),
                node.path("locationNote").asText(null));
    }

    private static Integer intOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<RefineDocumentResult> refine(RefineDocumentRequest request) {
        try {
            StringBuilder systemPrompt = new StringBuilder(
                    "You are a professional editor helping refine a job application document. "
                    + "The user will provide their current draft and a specific refinement request. "
                    + "Return ONLY the improved document text — no commentary, no explanations.");
            if (request.targetLanguage() != null && !request.targetLanguage().isBlank()) {
                systemPrompt.append(" Write in ").append(request.targetLanguage()).append(".");
            }
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("## Current Document\n").append(request.currentContent()).append("\n\n");
            if (request.jobDescription() != null && !request.jobDescription().isBlank()) {
                userPrompt.append("## Job Description Context\n").append(request.jobDescription()).append("\n\n");
            }
            userPrompt.append("## Refinement Request\n").append(request.userMessage());
            PromptComposition composition = new PromptComposition(
                    systemPrompt.toString(), userPrompt.toString(), "", "", "", "", userPrompt.toString());
            String refined = sanitizeAiText(aiProvider.generate(composition));
            return CompletableFuture.completedFuture(
                    new RefineDocumentResult(refined, aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Document refinement failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<ReviewDocumentResult> review(ReviewDocumentRequest request) {
        try {
            ReviewOutcome outcome = reviewContent(request.userId(), request.documentType(),
                    request.currentContent(), request.jobDescription(), request.targetLanguage());
            return CompletableFuture.completedFuture(
                    new ReviewDocumentResult(outcome.revised(), outcome.critique(), aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Document review failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Carrier for one reviewer pass: the revised draft plus what the reviewer changed/flagged. */
    private record ReviewOutcome(String revised, List<String> critique) {}

    /**
     * Drafter→reviewer pass: a fresh context critiques the draft against the posting and
     * the user's writing profile, then returns a revised version. Shared by the public
     * review endpoint and the automatic post-generation loop in {@link #generateDocument}.
     * The reviewer prompt lives server-side on purpose — it is not user-editable.
     */
    private ReviewOutcome reviewContent(UUID userId, String documentType, String currentContent,
                                        String jobDescription, String targetLanguage) {
        WritingProfile writingProfile = writingProfileRepo.findByUserId(userId).orElse(null);

        StringBuilder systemPrompt = new StringBuilder("""
                You are a demanding hiring manager reviewing a candidate's application document \
                with fresh eyes. Critique it against the job posting: missed keywords, weak or \
                generic framing, claims that overreach what a candidate could defend in an \
                interview, and mismatches with the requested writing style. Then produce a \
                revised version that fixes what you flagged. Never invent skills or experience \
                the draft does not already claim, and never claim the candidate built a tool they \
                merely used. Respond with ONLY valid JSON.""");
        systemPrompt.append("\n\n").append(PromptCompositionBuilder.UNTRUSTED_JOB_INPUT);
        if (targetLanguage != null && !targetLanguage.isBlank()) {
            systemPrompt.append(" Write the revised document in ").append(targetLanguage).append(".");
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("## Draft (").append(documentType != null ? documentType : "document")
                .append(")\n").append(currentContent).append("\n\n");
        if (jobDescription != null && !jobDescription.isBlank()) {
            userPrompt.append("## Job Description\n").append(jobDescription).append("\n\n");
        }
        String styleMemory = compositionBuilder.buildStyleMemory(writingProfile);
        if (!styleMemory.isBlank()) {
            userPrompt.append(styleMemory).append("\n\n");
        }
        userPrompt.append("""
                Return only valid JSON in exactly this shape:
                {
                  "revisedContent": "<the full revised document text>",
                  "critique": ["<what you changed or flagged — one point per entry, 2-6 entries>"]
                }""");

        PromptComposition composition = new PromptComposition(
                systemPrompt.toString(), userPrompt.toString(), "", "", "", "", userPrompt.toString());
        try {
            var node = objectMapper.readTree(AiResponseParser.extractJsonObject(
                    sanitizeAiText(aiProvider.generateJson(composition))));
            String revised = node.path("revisedContent").asText(null);
            if (revised == null || revised.isBlank()) {
                throw new IllegalStateException("Reviewer returned no revised content");
            }
            List<String> critique = new java.util.ArrayList<>();
            node.path("critique").forEach(c -> critique.add(c.asText()));
            return new ReviewOutcome(revised, critique);
        } catch (Exception e) {
            throw new IllegalStateException("Document review failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<StructuredDocument> generateDocument(
            UUID userId, String documentType, UUID jobId, String rawJobDescription,
            String templateId, UUID promptTemplateId, String customInstructions,
            String motivationText, String targetLanguage, boolean showProfileImage, DocumentTheme theme) {
        try {
            String jobDescription = jobId != null
                    ? jobRepo.findById(jobId).map(Job::descriptionClean).orElse(rawJobDescription)
                    : rawJobDescription;
            String contactFreeJson = careerProfileContext.buildJson(userId);

            PromptTemplate styleTemplate = promptTemplateId != null
                    ? promptTemplateRepo.findById(promptTemplateId).orElse(null)
                    : promptTemplateRepo.findSystemDefault(documentType).orElse(null);
            if (styleTemplate != null) promptTemplateRepo.incrementUsage(styleTemplate.id());

            PromptComposition composition = compositionBuilder.composeStructuredApplicationPrompt(
                    documentType, contactFreeJson, jobDescription,
                    customInstructions, motivationText, targetLanguage, styleTemplate,
                    writingProfileRepo.findByUserId(userId).orElse(null),
                    applicationRepo.findRecentOutcomeLessons(userId, 5));

            String json = AiResponseParser.extractJsonObject(
                    sanitizeAiText(aiProvider.generateJson(composition)).trim());

            ApplicationDocumentAiResponse aiResponse =
                    objectMapper.readValue(json, ApplicationDocumentAiResponse.class);
            validateApplicationResponse(aiResponse);

            // Automatic drafter→reviewer loop: a fresh reviewer critiques and revises the
            // body before assembly. Config-gated (each pass is one extra LLM call); stops
            // early once a pass reports no further critique. Keyword metadata from the
            // original pass is retained — the reviewer is instructed never to drop keywords.
            String body = maybeReview(userId, documentType, aiResponse.body(), jobDescription, targetLanguage);

            // Deterministic fact gate: flag (or block on) metric claims not supported by the profile.
            applyFactGuard(body, contactFreeJson, documentType);

            DocumentType type = parseDocumentType(documentType);
            StructuredDocument doc = buildApplicationDocument.buildApplicationDocument(
                    userId, type, body, templateId,
                    aiResponse.keywordCoverage(), aiResponse.matchedKeywords(),
                    aiResponse.missingKeywords(), showProfileImage, theme);

            return CompletableFuture.completedFuture(
                    persistGeneratedDocument.save(userId, jobId, doc, aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Structured document generation failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Runs up to {@code autoReviewMaxIterations} reviewer passes on the draft body when
     * auto-review is enabled, returning the improved text. A failed pass is non-fatal —
     * it logs and returns the best draft so far, so generation never breaks on the reviewer.
     */
    private String maybeReview(UUID userId, String documentType, String body,
                               String jobDescription, String targetLanguage) {
        if (!autoReviewEnabled) return body;
        String current = body;
        int passes = Math.max(1, autoReviewMaxIterations);
        for (int i = 1; i <= passes; i++) {
            try {
                ReviewOutcome outcome = reviewContent(userId, documentType, current, jobDescription, targetLanguage);
                if (outcome.revised() != null && !outcome.revised().isBlank()) {
                    current = outcome.revised();
                }
                log.info("Auto-review pass {}/{}: {} change(s) flagged", i, passes, outcome.critique().size());
                if (outcome.critique().isEmpty()) break; // reviewer found nothing more to fix
            } catch (Exception e) {
                log.warn("Auto-review pass {} failed, keeping current draft: {}", i, e.getMessage());
                break;
            }
        }
        return current;
    }

    /**
     * Runs the deterministic fact gate on the generated body against the source profile.
     * In {@code warn} mode (default) unsupported metric claims are logged; in {@code block}
     * mode their presence fails generation. The reviewer loop's honesty rules are the first
     * line of defence — this is the model-free backstop that catches invented/inflated numbers.
     */
    private void applyFactGuard(String body, String sourceProfileJson, String documentType) {
        if (!factGuardEnabled) return;
        DocumentFactGuard.FactAudit audit = factGuard.audit(body, sourceProfileJson);
        if (audit.clean()) return;
        String msg = "Fact guard: metric claim(s) not supported by the profile in "
                + (documentType != null ? documentType : "document") + ": " + audit.inventedMetrics();
        if ("block".equalsIgnoreCase(factGuardMode)) {
            throw new IllegalStateException(msg + " — generation blocked (app.ai.fact-guard.mode=block)");
        }
        log.warn(msg);
    }

    private static void validateApplicationResponse(ApplicationDocumentAiResponse response) {
        if (response.body() == null || response.body().isBlank()) {
            throw new IllegalArgumentException("AI response did not include a document body");
        }
    }

    private static DocumentType parseDocumentType(String type) {
        if (type == null) return DocumentType.COVER_LETTER;
        try { return DocumentType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid document type: " + type
                    + ". Valid types: " + java.util.Arrays.toString(DocumentType.values()));
        }
    }

    private static String sanitizeAiText(String text) {
        return AiResponseParser.sanitize(text);
    }

    private static String buildAnalysisPrompt(String cvContent, String jobDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this CV / career profile and provide specific, actionable feedback.\n\n");
        sb.append("## CV Content\n").append(cvContent).append("\n\n");
        if (jobDescription != null && !jobDescription.isBlank()) {
            sb.append("## Target Job Description\n").append(jobDescription).append("\n\n");
            sb.append("Judge the CV against THIS job: ATS keyword matching, experience alignment, and gaps.\n");
            sb.append("""

                    Respond with ONLY a JSON object in exactly this shape:
                    {
                      "score": <0-100 overall score>,
                      "summary": "<2-3 sentence overall verdict>",
                      "strengths": ["<what already works well>", ...],
                      "gaps": ["<missing keywords, weak areas, or misalignments>", ...],
                      "suggestions": ["<concrete, actionable improvement — one per entry>", ...],
                      "dimensions": {
                        "technicalSkills": <0-100 — required/preferred skills coverage>,
                        "experience": <0-100 — work-history domain and role-type alignment>,
                        "cultureFit": <0-100 — company culture signals vs the candidate's profile>,
                        "careerAlignment": <0-100 — growth path and motivation fit for this role>,
                        "location": "PASS|FLAG|FAIL — commute/remote/relocation feasibility",
                        "locationNote": "<one sentence explaining the location verdict, or null>"
                      }
                    }
                    Score each dimension independently; do not average them yourself.
                    Be honest about gaps — never assume skills the CV does not state.
                    Give 3-6 entries per list. Every suggestion must be actionable, not generic advice.""");
        } else {
            sb.append("No target job given — judge the CV on general strength: clarity, quantified achievements, ATS readiness.\n");
            sb.append("""

                    Respond with ONLY a JSON object in exactly this shape:
                    {
                      "score": <0-100 overall score>,
                      "summary": "<2-3 sentence overall verdict>",
                      "strengths": ["<what already works well>", ...],
                      "gaps": ["<missing keywords, weak areas, or misalignments>", ...],
                      "suggestions": ["<concrete, actionable improvement — one per entry>", ...]
                    }
                    Give 3-6 entries per list. Every suggestion must be actionable, not generic advice.""");
        }
        return sb.toString();
    }
}
