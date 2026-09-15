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
 * The drafter→reviewer pass: a fresh context critiques a draft
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

    /** The reviewer persona — a fresh, demanding hiring manager. Not user-editable. */
    private static final String REVIEWER_PERSONA = """
            You are a demanding hiring manager reviewing a candidate's application document \
            with fresh eyes. Critique it against the job posting: missed keywords, weak or \
            generic framing, claims that overreach what a candidate could defend in an \
            interview, and mismatches with the requested writing style. Then produce a \
            revised version that fixes what you flagged. Never invent skills or experience \
            the draft does not already claim, and never claim the candidate built a tool they \
            merely used. Respond with ONLY valid JSON.""";

    /** The fixed JSON response contract appended to every review prompt. */
    private static final String REVIEW_JSON_INSTRUCTION = """
            Return only valid JSON in exactly this shape:
            {
              "revisedContent": "<the full revised document text>",
              "critique": ["<what you changed or flagged — one point per entry, 2-6 entries>"]
            }
            Never drop a keyword the draft genuinely supports just to shorten it — coverage is
            measured against the delivered text after you are done.""";

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

    /** The stable inputs of a review — who, what document, and the posting/language it targets. */
    public record ReviewContext(UUID userId, String documentType, String jobDescription,
                                String targetLanguage, String jobCountry) {}

    /** Carrier for one reviewer pass: the revised draft and what the reviewer changed or flagged. */
    public record ReviewOutcome(String revised, List<String> critique) {}

    /** The result of one auto-review attempt: the best draft so far, and whether the loop should stop. */
    private record ReviewPass(String text, boolean done) {}

    /**
     * One drafter→reviewer pass: a fresh context critiques the draft against the posting and the
     * user's writing profile, then returns a revised version. Shared by the public review endpoint
     * and the automatic post-generation loop in {@link #autoReview}.
     */
    public ReviewOutcome review(ReviewContext ctx, String content) {
        WritingProfile writingProfile = writingProfileRepo.findByUserId(ctx.userId()).orElse(null);
        // The same guardrail envelope the drafting prompt uses, so a review pass can neither switch
        // language nor lose the market conventions the draft was written to. Keyed by the document
        // type, so a CV review is framed with CV conventions rather than a letter's.
        GenerationGuardrails guardrails = GenerationGuardrails.forDocument(
                ctx.documentType(), ctx.targetLanguage(), ctx.jobDescription(), ctx.jobCountry());
        // Deterministic filler findings are handed to the reviewer as concrete work: it is far
        // better at removing a phrase it has been shown than at avoiding one in the abstract.
        List<String> flaggedPhrases = clicheGuard.audit(content).phrases();

        String system = reviewSystemPrompt(guardrails);
        String user = reviewUserPrompt(ctx, content, writingProfile, guardrails, flaggedPhrases);
        PromptComposition composition = new PromptComposition(system, user, "", "", "", "", user);
        return parseReviewOutcome(aiProvider.generateJson(composition, AiOperations.DOCUMENT_REVIEW));
    }

    /** The reviewer system prompt: persona, the untrusted-input guard, and the resolved language. */
    static String reviewSystemPrompt(GenerationGuardrails guardrails) {
        StringBuilder sb = new StringBuilder(REVIEWER_PERSONA);
        sb.append("\n\n").append(guardrails.untrustedInputBlock());
        if (guardrails.resolvedLanguage() != null) {
            sb.append(" Write the revised document in ").append(guardrails.resolvedLanguage()).append(".");
        }
        return sb.toString();
    }

    /** The reviewer user prompt: the draft plus each applicable context/guardrail section, then the JSON contract. */
    String reviewUserPrompt(ReviewContext ctx, String content, WritingProfile writingProfile,
                            GenerationGuardrails guardrails, List<String> flaggedPhrases) {
        List<String> sections = List.of(
                draftBlock(ctx, content),
                jobDescriptionBlock(ctx),
                compositionBuilder.buildStyleMemory(writingProfile),
                guardrails.honestyRules(),
                guardrails.marketRules(),
                flaggedPhrasesBlock(flaggedPhrases),
                guardrails.clicheBlock());
        StringBuilder sb = new StringBuilder();
        sections.forEach(section -> appendBlock(sb, section));
        sb.append(REVIEW_JSON_INSTRUCTION);
        return sb.toString();
    }

    private static String draftBlock(ReviewContext ctx, String content) {
        String label = ctx.documentType() != null ? ctx.documentType() : "document";
        return "## Draft (" + label + ")\n" + content;
    }

    private static String jobDescriptionBlock(ReviewContext ctx) {
        return ctx.jobDescription() == null || ctx.jobDescription().isBlank()
                ? "" : "## Job Description\n" + ctx.jobDescription();
    }

    /** Appends a non-blank block followed by a blank line; a no-op for absent sections. */
    private static void appendBlock(StringBuilder sb, String block) {
        if (block != null && !block.isBlank()) {
            sb.append(block).append("\n\n");
        }
    }

    /** The flagged-filler-phrases section, or empty when the draft carried none. */
    static String flaggedPhrasesBlock(List<String> flaggedPhrases) {
        if (flaggedPhrases.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("## Flagged Filler Phrases\n")
                .append("A deterministic check found these phrases in the draft. Rewrite every one "
                        + "of them into something concrete and specific to this candidate and "
                        + "posting — do not simply delete the sentence if it carried a real point:");
        flaggedPhrases.forEach(phrase -> sb.append("\n- \"").append(phrase).append("\""));
        return sb.toString();
    }

    private ReviewOutcome parseReviewOutcome(String rawResponse) {
        try {
            JsonNode node = objectMapper.readTree(
                    AiResponseParser.extractJsonObject(AiResponseParser.sanitize(rawResponse)));
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
    public String autoReview(ReviewContext ctx, String body) {
        if (!autoReviewEnabled) {
            return body;
        }
        String current = body;
        int passes = Math.max(1, autoReviewMaxIterations);
        for (int i = 1; i <= passes; i++) {
            ReviewPass pass = reviewPass(ctx, current, i, passes);
            current = pass.text();
            if (pass.done()) {
                break;
            }
        }
        return current;
    }

    /**
     * One auto-review attempt. On success returns the revised draft and whether the reviewer is
     * exhausted (no further critique); on failure logs and returns the unchanged draft marked done,
     * so a broken pass ends the loop without breaking generation.
     */
    private ReviewPass reviewPass(ReviewContext ctx, String current, int index, int passes) {
        try {
            ReviewOutcome outcome = review(ctx, current);
            log.info("Auto-review pass {}/{}: {} change(s) flagged", index, passes, outcome.critique().size());
            return new ReviewPass(outcome.revised(), outcome.critique().isEmpty());
        } catch (Exception e) {
            log.warn("Auto-review pass {} failed, keeping current draft: {}", index, e.getMessage());
            return new ReviewPass(current, true);
        }
    }
}
