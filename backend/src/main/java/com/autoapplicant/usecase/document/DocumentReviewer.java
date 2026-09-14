package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The drafter→reviewer pass, extracted out of {@code AiService}: a fresh context critiques a draft
 * against the posting and the user's writing profile and returns a revised version, plus the
 * config-gated loop that runs it automatically after generation. Shared by the public review
 * endpoint and the generation path — a plain collaborator (in the vein of {@link CvDocumentAssembler}
 * / {@code TailoredCvGenerator}), so neither use case has to inject the other.
 *
 * <p>The reviewer prompt lives server-side on purpose — it is not user-editable.
 */
@Service
public class DocumentReviewer {

    private static final Logger log = LoggerFactory.getLogger(DocumentReviewer.class);

    private final ChatProviderPort aiProvider;
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final PromptCompositionBuilder compositionBuilder;
    private final ClicheGuard clicheGuard;
    private final ObjectMapper objectMapper;

    /** When true, {@link #autoReview} runs a reviewer critique/revise pass on the draft. */
    @Value("${app.ai.auto-review.enabled:true}")
    private boolean autoReviewEnabled;

    /** Max reviewer passes; the loop also stops early once a pass reports no further critique. */
    @Value("${app.ai.auto-review.max-iterations:1}")
    private int autoReviewMaxIterations;

    public DocumentReviewer(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                            WritingProfileRepositoryPort writingProfileRepo,
                            PromptCompositionBuilder compositionBuilder,
                            ClicheGuard clicheGuard,
                            ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.writingProfileRepo = writingProfileRepo;
        this.compositionBuilder = compositionBuilder;
        this.clicheGuard = clicheGuard;
        this.objectMapper = objectMapper;
    }

    /** Carrier for one reviewer pass: the revised draft and what the reviewer changed or flagged. */
    public record ReviewOutcome(String revised, List<String> critique) {}

    /**
     * One drafter→reviewer pass: a fresh context critiques the draft against the posting and the
     * user's writing profile, then returns a revised version. Shared by the public review endpoint
     * and the automatic post-generation loop in {@link #autoReview}.
     */
    public ReviewOutcome review(UUID userId, String documentType, String currentContent,
                                String jobDescription, String targetLanguage, String jobCountry) {
        WritingProfile writingProfile = writingProfileRepo.findByUserId(userId).orElse(null);
        // The same guardrail envelope the drafting prompt uses, so a review pass can neither switch
        // language nor lose the market conventions the draft was written to. Keyed by the document
        // type, so a CV review is framed with CV conventions rather than a letter's.
        GenerationGuardrails guardrails = GenerationGuardrails.forDocument(
                documentType, targetLanguage, jobDescription, jobCountry);
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
        systemPrompt.append("\n\n").append(guardrails.untrustedInputBlock());
        if (guardrails.resolvedLanguage() != null) {
            systemPrompt.append(" Write the revised document in ")
                    .append(guardrails.resolvedLanguage()).append(".");
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
        userPrompt.append(guardrails.honestyRules()).append("\n\n");
        if (!guardrails.marketRules().isBlank()) {
            userPrompt.append(guardrails.marketRules()).append("\n\n");
        }
        if (!flaggedPhrases.isEmpty()) {
            userPrompt.append("## Flagged Filler Phrases\n")
                    .append("A deterministic check found these phrases in the draft. Rewrite every one "
                            + "of them into something concrete and specific to this candidate and "
                            + "posting — do not simply delete the sentence if it carried a real point:\n");
            flaggedPhrases.forEach(phrase -> userPrompt.append("- \"").append(phrase).append("\"\n"));
            userPrompt.append('\n');
        }
        userPrompt.append(guardrails.clicheBlock()).append("\n\n");
        userPrompt.append("""
                Return only valid JSON in exactly this shape:
                {
                  "revisedContent": "<the full revised document text>",
                  "critique": ["<what you changed or flagged — one point per entry, 2-6 entries>"]
                }
                Never drop a keyword the draft genuinely supports just to shorten it — coverage is
                measured against the delivered text after you are done.""");

        PromptComposition composition = new PromptComposition(
                systemPrompt.toString(), userPrompt.toString(), "", "", "", "", userPrompt.toString());
        try {
            JsonNode node = objectMapper.readTree(AiResponseParser.extractJsonObject(
                    AiResponseParser.sanitize(aiProvider.generateJson(composition, AiOperations.DOCUMENT_REVIEW))));
            String revised = node.path("revisedContent").asText(null);
            if (revised == null || revised.isBlank()) {
                throw new IllegalStateException("Reviewer returned no revised content");
            }
            List<String> critique = new ArrayList<>();
            node.path("critique").forEach(c -> critique.add(c.asText()));
            return new ReviewOutcome(revised, critique);
        } catch (Exception e) {
            throw new IllegalStateException("Document review failed: " + e.getMessage(), e);
        }
    }

    /**
     * Runs up to {@code autoReviewMaxIterations} reviewer passes on the draft body when auto-review
     * is enabled, returning the improved text. A failed pass is non-fatal — it logs and returns the
     * best draft so far, so generation never breaks on the reviewer.
     */
    public String autoReview(UUID userId, String documentType, String body,
                             String jobDescription, String targetLanguage, String jobCountry) {
        if (!autoReviewEnabled) return body;
        String current = body;
        int passes = Math.max(1, autoReviewMaxIterations);
        for (int i = 1; i <= passes; i++) {
            try {
                ReviewOutcome outcome = review(userId, documentType, current, jobDescription,
                        targetLanguage, jobCountry);
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
}
