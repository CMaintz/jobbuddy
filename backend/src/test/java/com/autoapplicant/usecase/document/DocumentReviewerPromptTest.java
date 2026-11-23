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
    void userPromptJoinsEverySectionWithOneBlankLineThenTheJsonContract() {
        ReviewContext ctx = new ReviewContext(null, "COVER_LETTER", "the posting", "English", "DK", List.of());
        String user = reviewer.reviewUserPrompt(
                ctx, "MY DRAFT", null, guardrails("HONESTY", "MARKET", "CLICHE"), List.of("synergy", "leverage"));

        assertThat(user).isEqualTo(
                "## Draft (COVER_LETTER)\nMY DRAFT\n\n"
                + "## Job Description\nthe posting\n\n"
                + "HONESTY\n\n"
                + "MARKET\n\n"
                + LetterRules.BLOCK + "\n\n"
                + "## Flagged Filler Phrases\n"
                + "A deterministic check found these phrases in the draft. Rewrite every one of them "
                + "into something concrete and specific to this candidate and posting — do not simply "
                + "delete the sentence if it carried a real point:\n"
                + "- \"synergy\"\n- \"leverage\"\n\n"
                + "CLICHE\n\n"
                + "Return only valid JSON in exactly this shape:\n"
                + "{\n"
                + "  \"revisedContent\": \"<the full revised document text>\",\n"
                + "  \"critique\": [\"<what you changed or flagged — one point per entry, 2-6 entries>\"]\n"
                + "}\n"
                + "Never drop a keyword the draft genuinely supports just to shorten it — coverage is\n"
                + "measured against the delivered text after you are done.");
    }

    @Test
    void blankGuardrailSectionsAndAbsentPostingAreOmittedCleanly() {
        ReviewContext ctx = new ReviewContext(null, null, null, null, null, List.of());
        String user = reviewer.reviewUserPrompt(ctx, "DRAFT", null, guardrails("", "", ""), List.of());

        // No job-description header, no flagged block, no blank guardrail sections — just the draft
        // (labelled "document" when the type is null) and the JSON contract, one blank line apart.
        assertThat(user).startsWith("## Draft (document)\nDRAFT\n\nReturn only valid JSON");
    }

    @Test
    void aLetterIsReviewedByTheLetterRulesAndWrittenFromItsNeeds() {
        // Bug: the reviewer rewrites the letter without the rules or the needs and chases the
        // posting's keywords, undoing the structure the drafter was given before the gate sees it.
        ReviewContext ctx = new ReviewContext(null, "COVER_LETTER", "the posting", "Danish", "DK",
                List.of("680 chauffører uden skærm skal følge reglerne"));
        String user = reviewer.reviewUserPrompt(ctx, "DRAFT", null, guardrails("", "", ""), List.of());

        assertThat(user).contains(LetterRules.BLOCK, "## Employer Needs",
                "680 chauffører uden skærm skal følge reglerne");
    }

    @Test
    void aCvReviewGetsNeitherTheLetterRulesNorNeeds() {
        // Bug: one-paragraph-per-need letter rules reshape a CV under review.
        ReviewContext ctx = new ReviewContext(null, "CV", "the posting", "Danish", "DK", List.of("et behov"));
        String user = reviewer.reviewUserPrompt(ctx, "DRAFT", null, guardrails("", "", ""), List.of());

        assertThat(user).doesNotContain("## Letter Rules").doesNotContain("## Employer Needs");
    }

    @Test
    void flaggedPhrasesBlockIsEmptyWhenNothingFlagged() {
        assertThat(DocumentReviewer.flaggedPhrasesBlock(List.of())).isEmpty();
    }
}
