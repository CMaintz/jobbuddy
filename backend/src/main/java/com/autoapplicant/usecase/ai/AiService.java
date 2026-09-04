package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.*;
import com.autoapplicant.domain.document.*;
import com.autoapplicant.domain.document.QualityScore;
import com.autoapplicant.domain.document.RecordedQualityScore;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.in.ai.ReviewDocumentUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.BuildApplicationDocumentPort;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import com.autoapplicant.port.out.document.PersistGeneratedDocumentPort;
import com.autoapplicant.port.out.document.QualityScoreRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.JobLanguageDetector;
import com.autoapplicant.usecase.document.MarketConventions;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import com.autoapplicant.usecase.eval.DocumentQualityEvaluator;
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

    private final ChatProviderPort aiProvider;
    private final JobRepositoryPort jobRepo;
    private final CvVersionRepositoryPort cvRepo;
    private final PromptTemplateRepositoryPort promptTemplateRepo;
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final PromptCompositionBuilder compositionBuilder;
    private final CareerProfileContextService careerProfileContext;
    private final BuildApplicationDocumentPort buildApplicationDocument;
    private final PersistGeneratedDocumentPort persistGeneratedDocument;
    private final ApplicationRepositoryPort applicationRepo;
    private final GeneratedContentGuards contentGuards;
    private final ClicheGuard clicheGuard;
    private final DocumentQualityEvaluator qualityEvaluator;
    private final QualityScoreRepositoryPort qualityScoreRepo;
    private final CompanyGroundingService companyGrounding;
    private final AnalysisResponseParser analysisParser;
    private final ObjectMapper objectMapper;

    /** When true, generateDocument runs a reviewer critique/revise pass on the draft before assembling. */
    @org.springframework.beans.factory.annotation.Value("${app.ai.auto-review.enabled:true}")
    private boolean autoReviewEnabled;

    /** Max reviewer passes; the loop also stops early once a pass reports no further critique. */
    @org.springframework.beans.factory.annotation.Value("${app.ai.auto-review.max-iterations:1}")
    private int autoReviewMaxIterations;

    public AiService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                     JobRepositoryPort jobRepo,
                     CvVersionRepositoryPort cvRepo,
                     PromptTemplateRepositoryPort promptTemplateRepo,
                     WritingProfileRepositoryPort writingProfileRepo,
                     PromptCompositionBuilder compositionBuilder,
                     CareerProfileContextService careerProfileContext,
                     BuildApplicationDocumentPort buildApplicationDocument,
                     PersistGeneratedDocumentPort persistGeneratedDocument,
                     ApplicationRepositoryPort applicationRepo,
                     GeneratedContentGuards contentGuards,
                     ClicheGuard clicheGuard,
                     DocumentQualityEvaluator qualityEvaluator,
                     QualityScoreRepositoryPort qualityScoreRepo,
                     CompanyGroundingService companyGrounding,
                     AnalysisResponseParser analysisParser,
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
        this.contentGuards = contentGuards;
        this.clicheGuard = clicheGuard;
        this.qualityEvaluator = qualityEvaluator;
        this.qualityScoreRepo = qualityScoreRepo;
        this.companyGrounding = companyGrounding;
        this.analysisParser = analysisParser;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<AiAnalysisResult> analyze(UUID userId, UUID cvVersionId,
                                                       UUID jobId, String rawJobDescription) {
        try {
            // Explicit CV version wins; otherwise the PII-free master profile JSON.
            String cvContent = cvVersionId != null
                    ? cvRepo.findById(cvVersionId).map(CvVersion::content).orElse("")
                    : careerProfileContext.buildJson(userId);
            Job analysisJob = jobId != null ? jobRepo.findById(jobId).orElse(null) : null;
            String jobDesc = analysisJob != null ? analysisJob.descriptionClean() : rawJobDescription;
            String prompt = buildAnalysisPrompt(cvContent, jobDesc,
                    analysisJob != null ? analysisJob.country() : null);
            PromptComposition composition = new PromptComposition(
                    "You are an expert ATS reviewer and career coach. Analyze CVs and respond with JSON only. "
                    + "Never assume skills or experience the CV does not state.\n\n"
                    + PromptCompositionBuilder.UNTRUSTED_JOB_INPUT,
                    prompt, "", "", "", "", prompt);
            String response = sanitizeAiText(aiProvider.generateJson(composition, AiOperations.CV_ANALYSIS));
            return CompletableFuture.completedFuture(analysisParser.parse(response));
        } catch (Exception e) {
            log.error("CV analysis failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<RefineDocumentResult> refine(RefineDocumentRequest request) {
        try {
            // Refinement produces text the user sends. It was the one generation path with no
            // honesty rules, no banned phrases, no market conventions and no guard on its output —
            // so "make it stronger" was an unchecked invitation to invent.
            String contactFreeJson = careerProfileContext.buildJson(request.userId());
            String resolvedLanguage = JobLanguageDetector.resolve(
                    request.targetLanguage(), request.jobDescription());
            MarketConventions.Market market = MarketConventions.resolve(resolvedLanguage, null);
            String marketRules = MarketConventions.letterRules(market);

            StringBuilder systemPrompt = new StringBuilder(
                    "You are a professional editor helping refine a job application document. "
                    + "The user will provide their current draft and a specific refinement request. "
                    + "Edit what is there: you may cut, reorder, sharpen and rephrase, but you may "
                    + "not add a fact the draft does not already contain. A request to make the "
                    + "document stronger is a request to write better, never to claim more. "
                    + "Return ONLY the improved document text — no commentary, no explanations.");
            systemPrompt.append("\n\n").append(PromptCompositionBuilder.UNTRUSTED_JOB_INPUT);
            if (resolvedLanguage != null) {
                systemPrompt.append("\nWrite in ").append(resolvedLanguage).append(".");
            }

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("## Current Document\n").append(request.currentContent()).append("\n\n");
            if (request.jobDescription() != null && !request.jobDescription().isBlank()) {
                userPrompt.append("## Job Description Context\n").append(request.jobDescription()).append("\n\n");
            }
            userPrompt.append("## Refinement Request\n").append(request.userMessage());
            if (!marketRules.isBlank()) userPrompt.append("\n\n").append(marketRules);
            userPrompt.append("\n\n").append(ClicheGuard.promptBlock(resolvedLanguage));

            PromptComposition composition = new PromptComposition(
                    systemPrompt.toString(), userPrompt.toString(), "", "", "", "", userPrompt.toString());
            String refined = sanitizeAiText(aiProvider.generate(composition, AiOperations.DOCUMENT_REFINE));

            // Same backstops as generation: an edit can introduce a fabrication just as easily.
            contentGuards.verify(request.userId(), refined, contactFreeJson, "REFINEMENT");

            return CompletableFuture.completedFuture(
                    new RefineDocumentResult(refined, aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Document refinement failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<ReviewDocumentResult> review(ReviewDocumentRequest request) {
        try {
            ReviewOutcome outcome = reviewContent(request.userId(), request.documentType(),
                    request.currentContent(), request.jobDescription(), request.targetLanguage(), null);
            // The reviewer rewrites the whole document, so its output needs the same backstops as a
            // first draft — it was previously handed back unchecked.
            contentGuards.verify(request.userId(), outcome.revised(),
                    careerProfileContext.buildJson(request.userId()), request.documentType());
            return CompletableFuture.completedFuture(
                    new ReviewDocumentResult(outcome.revised(), outcome.critique(), aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Document review failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Carrier for one reviewer pass: the revised draft, what the reviewer changed/flagged, and
     * the keyword coverage recomputed against the REVISED text (so the ATS report the user sees
     * describes what was actually delivered, not the pre-review draft). Coverage fields are null
     * when the reviewer did not supply them.
     */
    private record ReviewOutcome(String revised, List<String> critique,
                                 Integer keywordCoverage, List<String> matchedKeywords,
                                 List<String> missingKeywords) {}

    /** The reviewed body plus the coverage metadata that describes it (null coverage = unchanged). */
    private record ReviewedBody(String body, Integer keywordCoverage,
                                List<String> matchedKeywords, List<String> missingKeywords) {}

    /**
     * Drafter→reviewer pass: a fresh context critiques the draft against the posting and
     * the user's writing profile, then returns a revised version. Shared by the public
     * review endpoint and the automatic post-generation loop in {@link #generateDocument}.
     * The reviewer prompt lives server-side on purpose — it is not user-editable.
     */
    private ReviewOutcome reviewContent(UUID userId, String documentType, String currentContent,
                                        String jobDescription, String targetLanguage, String jobCountry) {
        WritingProfile writingProfile = writingProfileRepo.findByUserId(userId).orElse(null);
        // Resolve language and market exactly as the drafting prompt did, so a review pass can
        // neither switch language nor lose the market conventions the draft was written to.
        String resolvedLanguage = JobLanguageDetector.resolve(targetLanguage, jobDescription);
        String marketRules = MarketConventions.letterRules(
                MarketConventions.resolve(resolvedLanguage, jobCountry));
        // Deterministic filler findings are handed to the reviewer as concrete work: it is far
        // better at removing a phrase it has been shown than at avoiding one in the abstract.
        List<String> flaggedPhrases = clicheGuard.audit(currentContent).phrases();

        StringBuilder systemPrompt = new StringBuilder("""
                You are a demanding hiring manager reviewing a candidate's application document \
                with fresh eyes. Critique it against the job posting: missed keywords, weak or \
                generic framing, claims that overreach what a candidate could defend in an \
                interview, and mismatches with the requested writing style. Then produce a \
                revised version that fixes what you flagged. Never invent skills or experience \
                the draft does not already claim, and never claim the candidate built a tool they \
                merely used. Respond with ONLY valid JSON.""");
        systemPrompt.append("\n\n").append(PromptCompositionBuilder.UNTRUSTED_JOB_INPUT);
        if (resolvedLanguage != null) {
            systemPrompt.append(" Write the revised document in ").append(resolvedLanguage).append(".");
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
        if (!marketRules.isBlank()) {
            userPrompt.append(marketRules).append("\n\n");
        }
        if (!flaggedPhrases.isEmpty()) {
            userPrompt.append("## Flagged Filler Phrases\n")
                    .append("A deterministic check found these phrases in the draft. Rewrite every one "
                            + "of them into something concrete and specific to this candidate and "
                            + "posting — do not simply delete the sentence if it carried a real point:\n");
            flaggedPhrases.forEach(phrase -> userPrompt.append("- \"").append(phrase).append("\"\n"));
            userPrompt.append('\n');
        }
        userPrompt.append(ClicheGuard.promptBlock(resolvedLanguage)).append("\n\n");
        userPrompt.append("""
                Return only valid JSON in exactly this shape:
                {
                  "revisedContent": "<the full revised document text>",
                  "critique": ["<what you changed or flagged — one point per entry, 2-6 entries>"],
                  "keywordCoverage": <0-100 integer — recompute for the REVISED text against the posting>,
                  "matchedKeywords": ["<posting keyword the revised text genuinely supports>"],
                  "missingKeywords": ["<posting keyword still not covered>"]
                }
                Recompute the keyword fields for your revised text; never drop a keyword the draft
                genuinely supports just to shorten it.""");

        PromptComposition composition = new PromptComposition(
                systemPrompt.toString(), userPrompt.toString(), "", "", "", "", userPrompt.toString());
        try {
            var node = objectMapper.readTree(AiResponseParser.extractJsonObject(
                    sanitizeAiText(aiProvider.generateJson(composition, AiOperations.DOCUMENT_REVIEW))));
            String revised = node.path("revisedContent").asText(null);
            if (revised == null || revised.isBlank()) {
                throw new IllegalStateException("Reviewer returned no revised content");
            }
            List<String> critique = new java.util.ArrayList<>();
            node.path("critique").forEach(c -> critique.add(c.asText()));
            Integer coverage = node.has("keywordCoverage") && node.path("keywordCoverage").isNumber()
                    ? node.path("keywordCoverage").asInt() : null;
            return new ReviewOutcome(revised, critique, coverage,
                    textList(node, "matchedKeywords"), textList(node, "missingKeywords"));
        } catch (Exception e) {
            throw new IllegalStateException("Document review failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<StructuredDocument> generateDocument(
            UUID userId, String documentType, UUID jobId, String rawJobDescription,
            String templateId, UUID promptTemplateId, String customInstructions,
            String motivationText, String targetLanguage, boolean showProfileImage, DocumentTheme theme,
            String lengthPreference) {
        try {
            Job job = jobId != null ? jobRepo.findById(jobId).orElse(null) : null;
            String jobDescription = job != null && job.descriptionClean() != null
                    ? job.descriptionClean() : rawJobDescription;
            String contactFreeJson = careerProfileContext.buildJson(userId);
            // Grounded company facts (cached; from the company's own site) so company references
            // in cover letters are accurate rather than parroted from the untrusted posting.
            String companyFacts = job != null ? companyGrounding.factsFor(job.companyId()) : null;

            PromptTemplate styleTemplate = promptTemplateId != null
                    ? promptTemplateRepo.findById(promptTemplateId).orElse(null)
                    // The user's own chosen default when they have one, the app's seeded prompt
                    // otherwise — switching a default is their row, never a write to app content.
                    : promptTemplateRepo.findDefaultFor(userId, documentType).orElse(null);
            if (styleTemplate != null) promptTemplateRepo.incrementUsage(styleTemplate.id());

            PostingContext posting = new PostingContext(jobDescription,
                    job != null ? job.country() : null,
                    job != null && job.contact() != null && job.contact().hasName()
                            ? job.contact().display() : null);

            PromptComposition composition = compositionBuilder.composeStructuredApplicationPrompt(
                    documentType, contactFreeJson, posting,
                    customInstructions, motivationText, targetLanguage, styleTemplate,
                    writingProfileRepo.findByUserId(userId).orElse(null),
                    applicationRepo.findRecentOutcomeLessons(userId, 5),
                    companyFacts, lengthPreference);

            String json = AiResponseParser.extractJsonObject(
                    sanitizeAiText(aiProvider.generateJson(composition, AiOperations.DOCUMENT_GENERATION)).trim());

            ApplicationDocumentAiResponse aiResponse =
                    objectMapper.readValue(json, ApplicationDocumentAiResponse.class);
            validateApplicationResponse(aiResponse);

            // Automatic drafter→reviewer loop: a fresh reviewer critiques and revises the
            // body before assembly. Config-gated (each pass is one extra LLM call); stops
            // early once a pass reports no further critique. Keyword metadata from the
            // original pass is retained — the reviewer is instructed never to drop keywords.
            ReviewedBody reviewed = maybeReview(userId, documentType, aiResponse.body(), jobDescription,
                    targetLanguage, job != null ? job.country() : null);
            String body = reviewed.body();

            // Deterministic backstops (fact gate + retracted claims + filler), shared with the CV
            // path. The findings ride along into the ATS report so the user sees them.
            ContentGuardFindings guardFindings =
                    contentGuards.verify(userId, body, contactFreeJson, documentType);

            // Prefer the reviewer's recomputed coverage (it describes the delivered text); fall
            // back to the drafter's metadata when the reviewer didn't revise or supply it.
            Integer coverage = reviewed.keywordCoverage() != null
                    ? reviewed.keywordCoverage() : aiResponse.keywordCoverage();
            List<String> matched = reviewed.matchedKeywords() != null
                    ? reviewed.matchedKeywords() : aiResponse.matchedKeywords();
            List<String> missing = reviewed.missingKeywords() != null
                    ? reviewed.missingKeywords() : aiResponse.missingKeywords();

            DocumentType type = parseDocumentType(documentType);
            StructuredDocument doc = buildApplicationDocument.buildApplicationDocument(
                    userId, type, body, templateId,
                    coverage, matched, missing, showProfileImage, theme, guardFindings);

            StructuredDocument saved = persistGeneratedDocument.save(
                    userId, jobId, doc, aiProvider.chatModelName());
            recordQualityScore(userId, saved, documentType, body, contactFreeJson, job, lengthPreference);

            return CompletableFuture.completedFuture(saved);
        } catch (Exception e) {
            log.error("Structured document generation failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Scores the delivered document, logs it, and records it against the saved document, so prompt
     * changes are observable on real output rather than only on test fixtures. Deterministic and
     * cheap — no model call — and never allowed to break generation: a scoring failure is a lost
     * metric, not a lost document.
     *
     * <p>Only prose letters are scored; the short recruiter and follow-up messages carry their own
     * word caps and would be measured against the wrong target.
     */
    private void recordQualityScore(UUID userId, StructuredDocument saved, String documentType,
                                    String body, String profileJson, Job job, String lengthPreference) {
        if (!PromptCompositionBuilder.isProseLetter(documentType)) return;
        try {
            List<String> keywords = new java.util.ArrayList<>();
            if (job != null) {
                if (job.technologies() != null) keywords.addAll(job.technologies());
                if (job.skills() != null) keywords.addAll(job.skills());
            }
            QualityScore score = qualityEvaluator.evaluate(body, profileJson, keywords,
                    PromptCompositionBuilder.letterWordTarget(lengthPreference));
            log.info("Quality score for {}: {} [{}]", documentType, score.total(),
                    score.dimensions().stream()
                            .map(d -> d.code() + "=" + d.score())
                            .collect(java.util.stream.Collectors.joining(" ")));
            qualityScoreRepo.save(new RecordedQualityScore(null, userId,
                    saved != null ? saved.generatedDocumentId() : null,
                    documentType, score, null));
        } catch (Exception e) {
            log.debug("Quality scoring failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Runs up to {@code autoReviewMaxIterations} reviewer passes on the draft body when
     * auto-review is enabled, returning the improved text together with the keyword coverage the
     * last successful pass recomputed for it (so the ATS report matches the delivered text). A
     * failed pass is non-fatal — it logs and returns the best draft so far, so generation never
     * breaks on the reviewer.
     */
    private ReviewedBody maybeReview(UUID userId, String documentType, String body,
                                     String jobDescription, String targetLanguage, String jobCountry) {
        if (!autoReviewEnabled) return new ReviewedBody(body, null, null, null);
        String current = body;
        Integer coverage = null;
        List<String> matched = null;
        List<String> missing = null;
        int passes = Math.max(1, autoReviewMaxIterations);
        for (int i = 1; i <= passes; i++) {
            try {
                ReviewOutcome outcome = reviewContent(userId, documentType, current, jobDescription,
                        targetLanguage, jobCountry);
                if (outcome.revised() != null && !outcome.revised().isBlank()) {
                    current = outcome.revised();
                    // Adopt the reviewer's recomputed coverage only when it also revised the body.
                    if (outcome.keywordCoverage() != null) coverage = outcome.keywordCoverage();
                    if (outcome.matchedKeywords() != null) matched = outcome.matchedKeywords();
                    if (outcome.missingKeywords() != null) missing = outcome.missingKeywords();
                }
                log.info("Auto-review pass {}/{}: {} change(s) flagged", i, passes, outcome.critique().size());
                if (outcome.critique().isEmpty()) break; // reviewer found nothing more to fix
            } catch (Exception e) {
                log.warn("Auto-review pass {} failed, keeping current draft: {}", i, e.getMessage());
                break;
            }
        }
        return new ReviewedBody(current, coverage, matched, missing);
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

    /** Reads a JSON string array into a list, or {@code null} when the field is absent/not an array. */
    private static List<String> textList(com.fasterxml.jackson.databind.JsonNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) return null;
        List<String> out = new java.util.ArrayList<>();
        arr.forEach(n -> {
            String v = n.asText(null);
            if (v != null && !v.isBlank()) out.add(v.strip());
        });
        return out;
    }

    private static String buildAnalysisPrompt(String cvContent, String jobDescription, String jobCountry) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this CV / career profile and provide specific, actionable feedback.\n\n");
        sb.append("## CV Content\n").append(cvContent).append("\n\n");
        if (jobDescription != null && !jobDescription.isBlank()) {
            sb.append("## Target Job Description\n").append(jobDescription).append("\n\n");
            sb.append("Judge the CV against THIS job: ATS keyword matching, experience alignment, and gaps.\n");
            String marketRules = MarketConventions.jobReadingRules(MarketConventions.resolve(
                    JobLanguageDetector.detect(jobDescription), jobCountry));
            if (!marketRules.isBlank()) sb.append('\n').append(marketRules).append('\n');
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
                      },
                      "risk": {
                        "legitimacy": "HIGH_CONFIDENCE|CAUTION|SUSPICIOUS - is this a real, active opening?",
                        "legitimacyNote": "<one sentence on the legitimacy verdict>",
                        "signals": [{"label":"<short risk label>","severity":"LOW|MEDIUM|HIGH","note":"<one sentence>"}],
                        "compensationReliability": "HIGH|MEDIUM|LOW|UNKNOWN - trust in advertised pay as real base",
                        "compensationNote": "<one sentence, or null>"
                      }
                    }
                    Assess "risk" (posting legitimacy, risk signals, compensation reliability) SEPARATELY
                    from the score - it must NEVER change the score or any dimension. Surface signals,
                    never accuse; note legitimate explanations. Use ghost-posting cues (stale or vague
                    posting, contradictory or unrealistic requirements, no concrete team or role detail),
                    and classify how far the advertised comp is trustworthy base pay vs variable / "up to" /
                    commission. Give 0-4 risk signals; omit the array if none.
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
