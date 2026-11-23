package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.CompanyContext;
import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The letter is written from the employer's needs; the requirement list is the CV's. A letter
 * given the itemised asks answers them one by one, which is the posting echo the gate fails.
 */
class LetterNeedsPromptTest {

    private static final String PROFILE = "{\"summary\":\"Compliance specialist\"}";
    private static final String ASK = "Udarbejde GDPR-procedurer for chaufførerne";

    private final PromptCompositionBuilder builder = new PromptCompositionBuilder();
    private final PostingContext posting = new PostingContext("Vi søger en compliance-medarbejder.", "DK", null,
            List.of(new JobRequirement(ASK, RequirementTier.REQUIRED, RequirementKind.OTHER, null)));

    private String letterPrompt(String documentType) {
        return builder.composeStructuredApplicationPrompt(documentType, PROFILE, posting, null, null, "Danish",
                null, null, List.of(), new CompanyContext(null, null), "STANDARD").resolvedFinalPrompt();
    }

    private String cvPrompt() {
        return builder.composeCvTailoringPrompt(PROFILE, posting, null, "Danish", null, null, List.of(), "STANDARD")
                .resolvedFinalPrompt();
    }

    @Test
    void aLetterDerivesTheEmployerNeedsBeforeItWritesTheBody() {
        // Bug: the needs come after the body in the answer, so the model writes the letter first
        // and fits needs to it afterwards; or they are never asked for.
        String prompt = letterPrompt("COVER_LETTER");

        assertThat(prompt).contains("\"needs\"");
        assertThat(prompt.indexOf("\"needs\"")).isLessThan(prompt.indexOf("\"body\""));
        assertThat(prompt).contains("never a task");
    }

    @Test
    void aLetterIsWrittenByTheLetterRulesWithoutTheItemisedAsks() {
        // Bug: the letter still gets the requirement list with "answer the REQUIRED asks first",
        // which pushes it into echoing the posting's task list.
        String prompt = letterPrompt("COVER_LETTER");

        assertThat(prompt).contains(LetterRules.BLOCK);
        assertThat(prompt).doesNotContain("What The Posting Asks For").doesNotContain(ASK);
    }

    @Test
    void theCvKeepsTheItemisedAsksAndNeverSeesTheNeeds() {
        // Bug: the needs step or the letter rules leak into the CV, or the CV loses the keyword
        // list the ATS match depends on.
        String prompt = cvPrompt();

        assertThat(prompt).contains("What The Posting Asks For", ASK);
        assertThat(prompt).doesNotContain("\"needs\"").doesNotContain("Letter Rules");
    }

    @Test
    void aShortRecruiterMessageGetsNeitherNeedsNorLetterRules() {
        // Bug: the one-page letter structure is forced on a three-line recruiter message.
        String prompt = letterPrompt("RECRUITER_MESSAGE");

        assertThat(prompt).doesNotContain("\"needs\"").doesNotContain("Letter Rules");
    }
}
