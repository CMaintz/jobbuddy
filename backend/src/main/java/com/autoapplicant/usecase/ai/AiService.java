package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.*;
import com.autoapplicant.domain.document.*;
import com.autoapplicant.domain.document.QualityScore;
import com.autoapplicant.domain.document.RecordedQualityScore;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.JobKeywords;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.BuildApplicationDocumentPort;
import com.autoapplicant.port.out.document.PersistGeneratedDocumentPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.document.QualityScoreRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.DocumentReviewer;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import com.autoapplicant.usecase.eval.DocumentQualityEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AiService implements GenerateDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final ChatProviderPort aiProvider;
    private final JobRepositoryPort jobRepo;
    private final PromptTemplateRepositoryPort promptTemplateRepo;
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final PromptCompositionBuilder compositionBuilder;
    private final CareerProfileContextService careerProfileContext;
    private final BuildApplicationDocumentPort buildApplicationDocument;
    private final PersistGeneratedDocumentPort persistGeneratedDocument;
    private final ApplicationRepositoryPort applicationRepo;
    private final GeneratedContentGuards contentGuards;
    private final DocumentReviewer reviewer;
    private final DocumentQualityEvaluator qualityEvaluator;
    private final QualityScoreRepositoryPort qualityScoreRepo;
    private final CompanyGroundingService companyGrounding;
    private final ObjectMapper objectMapper;

    public AiService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                     JobRepositoryPort jobRepo,
                     PromptTemplateRepositoryPort promptTemplateRepo,
                     WritingProfileRepositoryPort writingProfileRepo,
                     PromptCompositionBuilder compositionBuilder,
                     CareerProfileContextService careerProfileContext,
                     BuildApplicationDocumentPort buildApplicationDocument,
                     PersistGeneratedDocumentPort persistGeneratedDocument,
                     ApplicationRepositoryPort applicationRepo,
                     GeneratedContentGuards contentGuards,
                     DocumentReviewer reviewer,
                     DocumentQualityEvaluator qualityEvaluator,
                     QualityScoreRepositoryPort qualityScoreRepo,
                     CompanyGroundingService companyGrounding,
                     ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.jobRepo = jobRepo;
        this.promptTemplateRepo = promptTemplateRepo;
        this.writingProfileRepo = writingProfileRepo;
        this.compositionBuilder = compositionBuilder;
        this.careerProfileContext = careerProfileContext;
        this.buildApplicationDocument = buildApplicationDocument;
        this.persistGeneratedDocument = persistGeneratedDocument;
        this.applicationRepo = applicationRepo;
        this.contentGuards = contentGuards;
        this.reviewer = reviewer;
        this.qualityEvaluator = qualityEvaluator;
        this.qualityScoreRepo = qualityScoreRepo;
        this.companyGrounding = companyGrounding;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("userAiTaskExecutor")
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "objectMapper.readValue throws the checked JsonProcessingException; the "
                    + "catch also absorbs runtime failures so any generation error fails the "
                    + "returned future rather than escaping the async executor.")
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
                            ? job.contact().display() : null,
                    job != null ? job.requirements() : java.util.List.of());

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
            // early once a pass reports no further critique.
            String body = reviewer.autoReview(
                    new DocumentReviewer.ReviewContext(userId, documentType, jobDescription,
                            targetLanguage, job != null ? job.country() : null),
                    aiResponse.body());

            // Deterministic backstops (fact gate + retracted claims + filler), shared with the CV
            // path. The findings ride along into the ATS report so the user sees them.
            ContentGuardFindings guardFindings =
                    contentGuards.verify(userId, body, contactFreeJson, documentType);

            DocumentType type = parseDocumentType(documentType);
            // Keyword coverage is measured downstream against the delivered text and the
            // posting's own tiered asks — the model is no longer asked to grade itself.
            StructuredDocument doc = buildApplicationDocument.buildApplicationDocument(
                    userId, type, body, templateId,
                    JobKeywords.of(job), showProfileImage, theme, guardFindings);

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

}
