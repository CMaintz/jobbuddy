package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.*;
import com.autoapplicant.domain.document.*;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.document.BuildApplicationDocumentPort;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import com.autoapplicant.port.out.document.PersistGeneratedDocumentPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
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
public class AiService implements AnalyzeCvUseCase, RefineDocumentUseCase, GenerateDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiProviderPort aiProvider;
    private final JobRepositoryPort jobRepo;
    private final CvVersionRepositoryPort cvRepo;
    private final PromptTemplateRepositoryPort promptTemplateRepo;
    private final PromptCompositionBuilder compositionBuilder;
    private final CareerProfileContextService careerProfileContext;
    private final BuildApplicationDocumentPort buildApplicationDocument;
    private final PersistGeneratedDocumentPort persistGeneratedDocument;
    private final ObjectMapper objectMapper;

    public AiService(@Qualifier("generationAiProvider") AiProviderPort aiProvider,
                     JobRepositoryPort jobRepo,
                     CvVersionRepositoryPort cvRepo,
                     PromptTemplateRepositoryPort promptTemplateRepo,
                     PromptCompositionBuilder compositionBuilder,
                     CareerProfileContextService careerProfileContext,
                     BuildApplicationDocumentPort buildApplicationDocument,
                     PersistGeneratedDocumentPort persistGeneratedDocument,
                     ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.jobRepo = jobRepo;
        this.cvRepo = cvRepo;
        this.promptTemplateRepo = promptTemplateRepo;
        this.compositionBuilder = compositionBuilder;
        this.careerProfileContext = careerProfileContext;
        this.buildApplicationDocument = buildApplicationDocument;
        this.persistGeneratedDocument = persistGeneratedDocument;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<AiAnalysisResult> analyze(UUID cvVersionId, UUID jobId) {
        try {
            String cvContent = cvVersionId != null
                    ? cvRepo.findById(cvVersionId).map(CvVersion::content).orElse("") : "";
            String jobDesc = jobId != null
                    ? jobRepo.findById(jobId).map(Job::descriptionClean).orElse("") : "";
            String prompt = buildAnalysisPrompt(cvContent, jobDesc);
            PromptComposition composition = new PromptComposition(
                    "You are an expert ATS and career coach. Analyze CVs and provide actionable feedback.",
                    prompt, "", "", "", "", prompt);
            String response = aiProvider.generate(composition);
            return CompletableFuture.completedFuture(new AiAnalysisResult(List.of(response), 0, response));
        } catch (Exception e) {
            log.error("CV analysis failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
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
            String refined = aiProvider.generate(composition);
            return CompletableFuture.completedFuture(
                    new RefineDocumentResult(refined, aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Document refinement failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<StructuredDocument> generateDocument(
            UUID userId, String documentType, UUID jobId, String rawJobDescription,
            String templateId, UUID promptTemplateId, String customInstructions,
            String targetLanguage, boolean showProfileImage, DocumentTheme theme) {
        try {
            String jobDescription = jobId != null
                    ? jobRepo.findById(jobId).map(Job::descriptionClean).orElse(rawJobDescription)
                    : rawJobDescription;
            String contactFreeJson = careerProfileContext.buildJson(userId);

            PromptTemplate styleTemplate = promptTemplateId != null
                    ? promptTemplateRepo.findById(promptTemplateId).orElse(null)
                    : promptTemplateRepo.findSystemDefault(documentType).orElse(null);

            PromptComposition composition = compositionBuilder.composeStructuredApplicationPrompt(
                    documentType, contactFreeJson, jobDescription,
                    customInstructions, targetLanguage, styleTemplate);

            String json = AiResponseParser.extractJsonObject(
                    aiProvider.generateJson(composition).trim());

            ApplicationDocumentAiResponse aiResponse =
                    objectMapper.readValue(json, ApplicationDocumentAiResponse.class);
            validateApplicationResponse(aiResponse);

            DocumentType type = parseDocumentType(documentType);
            StructuredDocument doc = buildApplicationDocument.buildApplicationDocument(
                    userId, type, aiResponse.body(), templateId,
                    aiResponse.keywordCoverage(), aiResponse.matchedKeywords(),
                    aiResponse.missingKeywords(), showProfileImage, theme);

            return CompletableFuture.completedFuture(
                    persistGeneratedDocument.save(userId, jobId, doc, aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Structured document generation failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
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
        catch (IllegalArgumentException ex) { return DocumentType.COVER_LETTER; }
    }

    private static String buildAnalysisPrompt(String cvContent, String jobDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this CV and provide specific, actionable feedback:\n\n");
        sb.append("## CV Content\n").append(cvContent).append("\n\n");
        if (jobDescription != null && !jobDescription.isBlank()) {
            sb.append("## Target Job Description\n").append(jobDescription).append("\n\n");
            sb.append("Focus on: ATS keyword matching, relevant experience alignment, gaps.\n");
        }
        sb.append("Provide: 1) ATS optimization tips, 2) Key missing keywords, 3) Achievement improvements, 4) Readability score (1-10)");
        return sb.toString();
    }
}
