package com.autoapplicant.usecase.ai;

import com.autoapplicant.adapter.ai.PromptCompositionBuilder;
import com.autoapplicant.domain.ai.*;
import com.autoapplicant.domain.document.*;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.ai.AnalyzeCvUseCase;
import com.autoapplicant.port.in.ai.GenerateDocumentUseCase;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.document.*;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.StructuredDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AiService implements GenerateDocumentUseCase, AnalyzeCvUseCase, RefineDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiProviderPort aiProvider;
    private final JobRepositoryPort jobRepo;
    private final CvVersionRepositoryPort cvRepo;
    private final PromptTemplateRepositoryPort templateRepo;
    private final GeneratedDocumentRepositoryPort docRepo;
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final PromptCompositionBuilder compositionBuilder;
    private final CareerProfileContextService careerProfileContext;
    private final StructuredDocumentService structuredDocuments;
    private final ObjectMapper objectMapper;

    public AiService(AiProviderPort aiProvider, JobRepositoryPort jobRepo,
                     CvVersionRepositoryPort cvRepo, PromptTemplateRepositoryPort templateRepo,
                     GeneratedDocumentRepositoryPort docRepo,
                     WritingProfileRepositoryPort writingProfileRepo,
                     PromptCompositionBuilder compositionBuilder,
                     CareerProfileContextService careerProfileContext,
                     StructuredDocumentService structuredDocuments,
                     ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.jobRepo = jobRepo;
        this.cvRepo = cvRepo;
        this.templateRepo = templateRepo;
        this.docRepo = docRepo;
        this.writingProfileRepo = writingProfileRepo;
        this.compositionBuilder = compositionBuilder;
        this.careerProfileContext = careerProfileContext;
        this.structuredDocuments = structuredDocuments;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<AiGenerationResult> generate(AiGenerationRequest request) {
        try {
            Job job = request.jobId() != null ? jobRepo.findById(request.jobId()).orElse(null) : null;
            PromptTemplate template = request.promptTemplateId() != null
                    ? templateRepo.findById(request.promptTemplateId()).orElse(null) : null;
            WritingProfile writingProfile = writingProfileRepo.findByUserId(request.userId()).orElse(null);
            String contactFreeCareerProfileJson = careerProfileContext.buildJson(request.userId());

            if (template == null) {
                template = defaultTemplate(request.documentType());
            }

            PromptComposition composition = compositionBuilder.compose(
                    template, job, request.jobDescription(), writingProfile,
                    request.targetLanguage(), contactFreeCareerProfileJson);

            StringBuilder finalPrompt = new StringBuilder(composition.resolvedFinalPrompt());

            if (request.useStyleFromHistory()) {
                List<GeneratedDocument> pastDocs = docRepo.findRecentByUserIdAndType(
                        request.userId(), request.documentType().name(), 3);
                if (!pastDocs.isEmpty()) {
                    finalPrompt.append("\n\n## Past Writing Examples (match this style)");
                    for (int i = 0; i < pastDocs.size(); i++) {
                        finalPrompt.append("\n\n### Example ").append(i + 1).append("\n")
                                .append(pastDocs.get(i).content());
                    }
                }
            }

            if (request.customInstructions() != null && !request.customInstructions().isBlank()) {
                finalPrompt.append("\n\nAdditional instructions: ").append(request.customInstructions());
            }

            composition = new PromptComposition(
                    composition.systemPrompt(), composition.userPromptTemplate(),
                    composition.cvContext(), composition.jobDescription(),
                    composition.writingStyleMemory(), composition.outputConstraints(),
                    finalPrompt.toString());

            String content = aiProvider.generate(composition);

            GeneratedDocument savedDoc = docRepo.save(new GeneratedDocument(
                    null, request.userId(), null, request.jobId(),
                    request.documentType(), content,
                    request.promptTemplateId(), request.cvVersionId(),
                    "gpt-4o", null, Instant.now()));

            return CompletableFuture.completedFuture(
                    new AiGenerationResult(content, "gpt-4o", null, Instant.now()));
        } catch (Exception e) {
            log.error("AI generation failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<AiAnalysisResult> analyze(AiAnalysisRequest request) {
        try {
            String prompt = buildAnalysisPrompt(request);
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
                    "You are a professional editor helping refine a job application document. " +
                    "The user will provide their current draft and a specific refinement request. " +
                    "Return ONLY the improved document text — no commentary, no explanations.");
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
            return CompletableFuture.completedFuture(new RefineDocumentResult(refined, "gpt-4o"));
        } catch (Exception e) {
            log.error("Document refinement failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<StructuredDocument> generateDocument(
            UUID userId, String documentType, UUID jobId, String rawJobDescription,
            String templateId, String customInstructions, String targetLanguage) {
        try {
            String jobDescription = jobId != null
                    ? jobRepo.findById(jobId).map(j -> j.descriptionClean()).orElse(rawJobDescription)
                    : rawJobDescription;
            String contactFreeJson = careerProfileContext.buildJson(userId);

            PromptComposition composition = compositionBuilder.composeStructuredApplicationPrompt(
                    documentType, contactFreeJson, jobDescription, customInstructions, targetLanguage);

            String json = aiProvider.generate(composition).trim();
            if (json.startsWith("```")) {
                int first = json.indexOf('\n');
                int last = json.lastIndexOf("```");
                if (first >= 0 && last > first) json = json.substring(first + 1, last).trim();
            }

            ApplicationDocumentAiResponse aiResponse = objectMapper.readValue(json, ApplicationDocumentAiResponse.class);

            DocumentType type = parseDocumentType(documentType);
            StructuredDocument doc = structuredDocuments.buildApplicationDocument(
                    userId, type, aiResponse.body(), templateId,
                    aiResponse.keywordCoverage(), aiResponse.matchedKeywords(), aiResponse.missingKeywords());

            return CompletableFuture.completedFuture(doc);
        } catch (Exception e) {
            log.error("Structured document generation failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private static DocumentType parseDocumentType(String type) {
        if (type == null) return DocumentType.COVER_LETTER;
        try { return DocumentType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException ex) { return DocumentType.COVER_LETTER; }
    }

    private String buildAnalysisPrompt(AiAnalysisRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this CV and provide specific, actionable feedback:\n\n");
        sb.append("## CV Content\n").append(request.cvContent()).append("\n\n");
        if (request.jobDescription() != null && !request.jobDescription().isBlank()) {
            sb.append("## Target Job Description\n").append(request.jobDescription()).append("\n\n");
            sb.append("Focus on: ATS keyword matching, relevant experience alignment, gaps.\n");
        }
        sb.append("Provide: 1) ATS optimization tips, 2) Key missing keywords, 3) Achievement improvements, 4) Readability score (1-10)");
        return sb.toString();
    }

    private PromptTemplate defaultTemplate(DocumentType documentType) {
        String prompt = switch (documentType) {
            case CV -> """
                    Create a focused, tailored CV based on the contact-free master career profile and job description provided.
                    Reorder and emphasise sections that are most relevant to the role.
                    Incorporate keywords from the job description naturally.
                    Keep achievements quantified where the source profile provides numbers.
                    Preserve all factual information — do not invent experience or credentials.
                    Do not include the user's name, contact details, profile image, LinkedIn, GitHub, or website.
                    Format as clean plain text with clear section headings.""";
            case COVER_LETTER -> "Write a compelling cover letter based on the contact-free master career profile and job description provided. Focus on relevant experience and genuine enthusiasm. Do not include the user's name or contact details.";
            case APPLICATION_TEXT -> "Write a professional job application text based on the contact-free master career profile and job description. Be concise and highlight key qualifications. Do not include the user's name or contact details.";
            case RECRUITER_MESSAGE -> "Write a brief, personalized recruiter message about this position. Keep it under 150 words. Do not include the user's name, contact details, LinkedIn, GitHub, or website.";
            case FOLLOW_UP_MESSAGE -> "Write a polite follow-up message to send after applying for the job. Reference the role specifically and express continued interest. Keep it under 100 words. Do not include the user's name or contact details.";
            default -> "Analyze and provide professional feedback based on the provided context.";
        };
        return new PromptTemplate(null, null, "Default " + documentType.name(), null, null,
                null, prompt, null, false, null, 1, null, null, false);
    }
}
