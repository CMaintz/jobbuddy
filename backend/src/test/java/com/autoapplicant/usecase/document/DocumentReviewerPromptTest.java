package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.usecase.document.DocumentReviewer.ReviewContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the reviewer prompt assembly (section order and the blank line between sections) so a
 * future edit to the builder cannot silently shift the delivered prompt. Uses small synthetic
 * guardrail blocks rather than the real (large) constants: the concern here is how the sections are
 * joined, not the text of each guardrail, which lives in {@link GenerationGuardrails}/{@link ClicheGuard}.
 */
class DocumentReviewerPromptTest {

    // The builder is stateless; the reviewer's other collaborators are unused by the prompt methods.
    private final DocumentReviewer reviewer =
            new DocumentReviewer(null, null, new PromptCompositionBuilder(), null, null);

    private static GenerationGuardrails guardrails(String honesty, String market, String cliche) {
        return new GenerationGuardrails("English", market, "UNTRUSTED", cliche, honesty);
    }

    @Test
    void systemPromptIsPersonaThenUntrustedGuardThenLanguage() {
        String system = DocumentReviewer.reviewSystemPrompt(guardrails("H", "M", "C"));
        assertThat(system)
                .contains("demanding hiring manager")
                .endsWith("\n\nUNTRUSTED Write the revised document in English.");
    }

    @Test
    void userPromptPutsEverySectionInOrderBeforeTheJsonContract() {
        ReviewContext ctx = new ReviewContext(null, "COVER_LETTER", "the posting", "English", "DK");
        String user = reviewer.reviewUserPrompt(
                ctx, "MY DRAFT", null, guardrails("HONESTY", "MARKET", "CLICHE"), List.of("synergy", "leverage"));

        // The JSON contract must come last: the model weighs the closing instruction most heavily,
        // and anything after it reads as part of the output format.
        List<Integer> positions = List.of(
                user.indexOf("## Draft (COVER_LETTER)\nMY DRAFT"),
                user.indexOf("## Job Description\nthe posting"),
                user.indexOf("HONESTY"),
                user.indexOf("MARKET"),
                user.indexOf("## Flagged Filler Phrases"),
                user.indexOf("- \"synergy\"\n- \"leverage\""),
                user.indexOf("CLICHE"),
                user.indexOf("Return only valid JSON"));
        assertThat(positions).doesNotContain(-1).isSorted();
        assertThat(user).contains("the posting\n\nHONESTY\n\nMARKET\n\n## Flagged")
                .contains("- \"leverage\"\n\nCLICHE\n\nReturn only valid JSON");
    }

    @Test
    void blankGuardrailSectionsAndAbsentPostingAreOmittedCleanly() {
        ReviewContext ctx = new ReviewContext(null, null, null, null, null);
        String user = reviewer.reviewUserPrompt(ctx, "DRAFT", null, guardrails("", "", ""), List.of());

        // No job-description header, no flagged block, no blank guardrail sections — just the draft
        // (labelled "document" when the type is null) and the JSON contract, one blank line apart.
        assertThat(user).startsWith("## Draft (document)\nDRAFT\n\nReturn only valid JSON");
    }
}
