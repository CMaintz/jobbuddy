package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.autoapplicant.usecase.ai.AiOperations;

/**
 * Drafter→reviewer pass for the structured CV — the CV-side counterpart to the cover-letter
 * reviewer in {@code AiService}. A fresh context critiques the tailored CV JSON against the
 * posting and the writing profile and returns a revised {@link TailoredCvContent} in the SAME
 * schema, preserving {@code sourceId}s so the assembler can still map items back to the master
 * profile. Any failure is non-fatal: the original draft is returned unchanged.
 *
 * <p>Config-gated by the same {@code app.ai.auto-review.enabled} flag as the letter reviewer.
 * The deterministic fact/retracted guards still run on the result downstream, and the assembler
 * re-validates every item against the source — so this pass can only reshape, never fabricate.
 */
@Service
public class TailoredCvReviewer {

    private static final Logger log = LoggerFactory.getLogger(TailoredCvReviewer.class);

    private final ChatProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final PromptCompositionBuilder promptBuilder;

    @Value("${app.ai.auto-review.enabled:true}")
    private boolean autoReviewEnabled;

    public TailoredCvReviewer(@Qualifier("generationAiProvider") ChatProviderPort aiProvider,
                              ObjectMapper objectMapper,
                              PromptCompositionBuilder promptBuilder) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    /** Critiques and revises the tailored CV; returns the draft unchanged when disabled or on error. */
    public TailoredCvContent review(TailoredCvContent draft, PostingContext posting,
                                    WritingProfile writingProfile, String targetLanguage) {
        if (!autoReviewEnabled || draft == null) return draft;
        try {
            String draftJson = objectMapper.writeValueAsString(draft);
            String jobDescription = posting != null ? posting.description() : null;
            // Same language/market resolution as the drafting prompt, so the reviewer cannot
            // quietly switch language or drop the market conventions the draft was written to.
            String resolvedLanguage = JobLanguageDetector.resolve(targetLanguage, jobDescription);
            String marketRules = MarketConventions.cvRules(MarketConventions.resolve(
                    resolvedLanguage, posting != null ? posting.country() : null));

            String system = """
                    You are a demanding hiring manager reviewing a candidate's tailored CV with fresh \
                    eyes, provided as structured JSON. Critique it against the posting: missed keywords \
                    the profile genuinely supports, weak or generic bullet phrasing, buried proof points, \
                    and claims that overreach what the candidate could defend in an interview. Then return \
                    a REVISED version in the identical JSON schema. Rules: keep every sourceId unchanged; \
                    never invent employers, titles, dates, schools, credentials, technologies, outcomes, \
                    or metrics not already present; you may only reselect, reorder within a section, and \
                    rewrite phrasing. Recompute keywordCoverage/matchedKeywords/missingKeywords for your \
                    revised content. Respond with ONLY valid JSON."""
                    + "\n\n" + PromptCompositionBuilder.UNTRUSTED_JOB_INPUT;
            if (resolvedLanguage != null) {
                system += "\nWrite all rewritten text in " + resolvedLanguage + ".";
            }

            String styleMemory = promptBuilder.buildStyleMemory(writingProfile);
            String user = "## Draft CV (JSON)\n" + draftJson
                    + "\n\n## Job Description\n" + (jobDescription != null ? jobDescription : "(none provided)")
                    + (styleMemory.isBlank() ? "" : "\n\n" + styleMemory)
                    + (marketRules.isBlank() ? "" : "\n\n" + marketRules)
                    + "\n\n" + ClicheGuard.promptBlock(resolvedLanguage)
                    + "\n\nReturn the revised CV in exactly the same JSON shape as the draft above.";

            PromptComposition composition = new PromptComposition(system, user, "", "", "", "", user);
            String json = AiResponseParser.extractJsonObject(
                    AiResponseParser.sanitize(aiProvider.generateJson(composition, AiOperations.TAILORED_CV_REVIEW)).trim());
            TailoredCvContent revised = objectMapper.readValue(json, TailoredCvContent.class);
            return revised != null ? revised : draft;
        } catch (Exception e) {
            log.warn("Tailored-CV review failed, keeping original draft: {}", e.getMessage());
            return draft;
        }
    }
}
