package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.RefineDocumentRequest;
import com.autoapplicant.domain.ai.RefineDocumentResult;
import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.in.ai.RefineDocumentUseCase;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.document.CvSectionPromptsRepositoryPort;
import com.autoapplicant.usecase.document.AiResponseParser;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import com.autoapplicant.usecase.document.GenerationGuardrails;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Refines an existing draft against a user request. It produces text the candidate sends, so it
 * carries the same guardrail envelope as a first draft — market conventions, banned phrases, the
 * injection guard and language — plus an inline honesty instruction and the deterministic fact gate
 * on its output. "Make it stronger" must never become licence to invent.
 */
@Service
public class DocumentRefinementService implements RefineDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DocumentRefinementService.class);

    /** The editor persona. The "write better, never claim more" clause is the anti-invention rule. */
    private static final String EDITOR_PERSONA =
            "You are a professional editor helping refine a job application document. "
            + "The user will provide their current draft and a specific refinement request. "
            + "Edit what is there: you may cut, reorder, sharpen and rephrase, but you may "
            + "not add a fact the draft does not already contain. A request to make the "
            + "document stronger is a request to write better, never to claim more. "
            + "Return ONLY the improved document text — no commentary, no explanations.";

    private final ChatProviderPort aiProvider;
    private final CareerProfileContextService careerProfileContext;
    private final GeneratedContentGuards contentGuards;
    private final CvSectionPromptsRepositoryPort cvSectionPromptsRepo;

    public DocumentRefinementService(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                                     CareerProfileContextService careerProfileContext,
                                     GeneratedContentGuards contentGuards,
                                     CvSectionPromptsRepositoryPort cvSectionPromptsRepo) {
        this.aiProvider = aiProvider;
        this.careerProfileContext = careerProfileContext;
        this.contentGuards = contentGuards;
        this.cvSectionPromptsRepo = cvSectionPromptsRepo;
    }

    @Override
    @Async("userAiTaskExecutor")
    public CompletableFuture<RefineDocumentResult> refine(RefineDocumentRequest request) {
        try {
            String contactFreeJson = careerProfileContext.buildJson(request.userId());
            GenerationGuardrails guardrails = GenerationGuardrails.forMedium(
                    GenerationGuardrails.Medium.LETTER, request.targetLanguage(),
                    request.jobDescription(), null);

            String user = refineUserPrompt(request, guardrails, savedSectionPrompt(request));
            PromptComposition composition = new PromptComposition(
                    refineSystemPrompt(guardrails), user, "", "", "", "", user);
            String refined = AiResponseParser.sanitize(aiProvider.generate(composition, AiOperations.DOCUMENT_REFINE));

            // Same backstops as generation: an edit can introduce a fabrication just as easily.
            contentGuards.verify(request.userId(), refined, contactFreeJson, "REFINEMENT");

            return CompletableFuture.completedFuture(
                    new RefineDocumentResult(refined, aiProvider.chatModelName()));
        } catch (Exception e) {
            log.error("Document refinement failed: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /** The editor persona, the untrusted-input guard, and the resolved output language. */
    static String refineSystemPrompt(GenerationGuardrails guardrails) {
        StringBuilder sb = new StringBuilder(EDITOR_PERSONA);
        sb.append("\n\n").append(guardrails.untrustedInputBlock());
        if (guardrails.resolvedLanguage() != null) {
            sb.append("\nWrite in ").append(guardrails.resolvedLanguage()).append(".");
        }
        return sb.toString();
    }

    /**
     * The draft, the optional posting context, the request, the user's standing section guidance (when
     * they saved one for the section being refined), and the writing guardrails — joined by blank lines.
     */
    static String refineUserPrompt(RefineDocumentRequest request, GenerationGuardrails guardrails,
                                   String savedSectionPrompt) {
        List<String> sections = List.of(
                "## Current Document\n" + request.currentContent(),
                jobDescriptionContext(request),
                "## Refinement Request\n" + request.userMessage(),
                sectionGuidanceBlock(savedSectionPrompt),
                guardrails.honestyRules(),
                guardrails.marketRules(),
                guardrails.clicheBlock());
        return sections.stream().filter(section -> !section.isBlank()).collect(Collectors.joining("\n\n"));
    }

    private static String sectionGuidanceBlock(String savedSectionPrompt) {
        return savedSectionPrompt == null || savedSectionPrompt.isBlank() ? ""
                : "## Your Standing Guidance For This Section\n" + savedSectionPrompt
                  + "\nApply it within the honesty rules; it never licenses adding facts the draft lacks.";
    }

    /** The user's saved prompt for the refined section, or null. Never fails the refine. */
    private String savedSectionPrompt(RefineDocumentRequest request) {
        if (request.sectionKey() == null || request.userId() == null) {
            return null;
        }
        try {
            return CvSection.fromKey(request.sectionKey())
                    .flatMap(section -> cvSectionPromptsRepo.findByUserId(request.userId())
                            .map(prompts -> prompts.forSection(section)))
                    .filter(prompt -> prompt != null && !prompt.isBlank())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Loading section prompt for refine failed: {}", e.getMessage());
            return null;
        }
    }

    private static String jobDescriptionContext(RefineDocumentRequest request) {
        return request.jobDescription() == null || request.jobDescription().isBlank()
                ? "" : "## Job Description Context\n" + request.jobDescription();
    }
}
