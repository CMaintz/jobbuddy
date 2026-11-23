package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.LetterGatePoint;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The gate's answer is the model's, but the verdict is ours: points 1 and 14 are recomputed from
 * the counts the gate reports, a missing point is a fail, and an answer we cannot read is no
 * verdict at all rather than a pass.
 */
class LetterGateTest {

    private static final String LETTER = "Jeres 680 chauffører skal kunne følge reglerne uden en skærm.";
    private static final String POSTING = "Vi søger en compliance-medarbejder.";
    private static final LetterBrief BRIEF = new LetterBrief("COVER_LETTER", POSTING, "Danish", "DK");

    /** Answers each gate call with the next scripted answer, or throws when told to. */
    static final class ScriptedModel implements ChatProviderPort {
        final Deque<String> answers = new ArrayDeque<>();
        final List<PromptComposition> prompts = new ArrayList<>();

        ScriptedModel answer(String json) {
            answers.add(json);
            return this;
        }

        @Override
        public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
            prompts.add(composition);
            String next = answers.poll();
            if (next == null || next.equals("THROW")) {
                throw new IllegalStateException("provider down");
            }
            return AiCompletion.untracked(next, "scripted");
        }

        @Override
        public String chatModelName() {
            return "scripted";
        }
    }

    static String answer(String point1, String point8, String point13, String point14, String verdict) {
        return "{\"points\":[" + String.join(",", point1, point8, point13, point14) + "],\"verdict\":\"" + verdict + "\"}";
    }

    static String point1(boolean passed, int employer, int total) {
        return "{\"point\":1,\"passed\":" + passed + ",\"employerSentences\":" + employer
                + ",\"totalSentences\":" + total + ",\"quotes\":[],\"reason\":\"farvetest\"}";
    }

    static String plain(int point, boolean passed, String... quotes) {
        StringBuilder q = new StringBuilder();
        for (String quote : quotes) {
            q.append(q.length() == 0 ? "" : ",").append('"').append(quote).append('"');
        }
        return "{\"point\":" + point + ",\"passed\":" + passed + ",\"quotes\":[" + q + "],\"reason\":\"r\"}";
    }

    static String point14(boolean passed, String perParagraph, int openings) {
        return "{\"point\":14,\"passed\":" + passed + ",\"pastTenseApplicantSentencesPerParagraph\":"
                + perParagraph + ",\"paragraphsOpeningWithApplicantInPast\":" + openings
                + ",\"quotes\":[],\"reason\":\"cv-referat\"}";
    }

    static String passing() {
        return answer(point1(true, 9, 14), plain(8, true), plain(13, true), point14(true, "[1,0,1]", 0),
                "PORT: BESTÅET");
    }

    private final ScriptedModel model = new ScriptedModel();

    private LetterGateResult judge() {
        return new LetterGate(model, new ObjectMapper()).judge(LETTER, BRIEF);
    }

    @Test
    void aLetterPassingAllFourPointsPasses() {
        // Bug: a well-formed passing answer is not recognised and every letter is rewritten.
        model.answer(passing());

        LetterGateResult result = judge();

        assertThat(result.verdict()).isEqualTo(LetterGateVerdict.PASSED);
        assertThat(result.points()).extracting(LetterGatePoint::point).containsExactly(1, 8, 13, 14);
    }

    @Test
    void aFailedPointCarriesTheQuotedSentences() {
        // Bug: the quotes are dropped, so the rewrite has nothing to act on and the user cannot
        // see what failed.
        model.answer(answer(point1(true, 9, 14), plain(8, true),
                plain(13, false, "I søger en, der kan X, Y og Z."), point14(true, "[1]", 0), "PORT: DUMPET"));

        LetterGateResult result = judge();

        assertThat(result.verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(result.failedPoints()).singleElement()
                .satisfies(p -> assertThat(p.quotes()).containsExactly("I søger en, der kan X, Y og Z."));
    }

    @Test
    void pointFourteenFailsOnItsCountsWhateverTheGateConcluded() {
        // Bug: the gate's own "passed" is trusted although it counted two past-tense sentences
        // about her in one paragraph, which the guide fails outright.
        model.answer(answer(point1(true, 9, 14), plain(8, true), plain(13, true),
                point14(true, "[0,2,1]", 0), "PORT: BESTÅET"));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void pointFourteenFailsWhenAParagraphOpensWithHerInThePast() {
        // Bug: a paragraph opening "Hos X ..." is let through because the per-paragraph count is low.
        model.answer(answer(point1(true, 9, 14), plain(8, true), plain(13, true),
                point14(true, "[1,1]", 1), "PORT: BESTÅET"));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void pointOneFailsWhenFewerThanHalfTheSentencesAreAboutTheEmployer() {
        // Bug: the gate passes a letter that is mostly about her on its own say-so.
        model.answer(answer(point1(true, 5, 14), plain(8, true), plain(13, true),
                point14(true, "[1]", 0), "PORT: BESTÅET"));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void aMissingPointOrMissingCountsIsAFail() {
        // Bug: an answer that skips point 8, or reports no counts for point 1, is read as a pass;
        // the guide says doubt counts as a fail.
        model.answer("{\"points\":[" + point1(true, 9, 14) + "," + plain(13, true) + ","
                + point14(true, "[1]", 0) + "],\"verdict\":\"PORT: BESTÅET\"}");

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void aPortLineSayingPassedDoesNotOverrideAFailedPoint() {
        // Bug: the final PORT line is trusted over the points, so a letter with a failed point
        // is exported as passed.
        model.answer(answer(point1(true, 9, 14), plain(8, false, "Jeg har arbejdet med X."),
                plain(13, true), point14(true, "[1]", 0), "PORT: BESTÅET"));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void aQuotedEchoSentenceFailsPointThirteenWhateverTheGateConcluded() {
        // Bug: the gate quotes a sentence listing the posting's tasks but marks the point passed,
        // and the echo reaches the employer.
        model.answer(answer(point1(true, 9, 14), plain(8, true),
                plain(13, true, "I søger en, der kan X, Y og Z."), point14(true, "[1]", 0), "PORT: BESTÅET"));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.FAILED);
    }

    @Test
    void allPointsPassingUnderAPortLineSayingFailedIsNoVerdict() {
        // Bug: the final PORT line is ignored, so a gate that concluded DUMPET without saying which
        // point failed is stored as a pass.
        model.answer(answer(point1(true, 9, 14), plain(8, true), plain(13, true),
                point14(true, "[1]", 0), "PORT: DUMPET")).answer(passing().replace("PORT: BESTÅET", "PORT: DUMPET"));

        LetterGateResult result = judge();

        assertThat(result.verdict()).isEqualTo(LetterGateVerdict.NOT_COMPLETED);
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void aMissingOrMalformedPortLineIsNotAPass() {
        // Bug: a verdict line the gate garbled ("PORT: OK") or left out is read as a pass.
        model.answer(passing().replace("PORT: BESTÅET", "PORT: OK"))
                .answer(passing().replace(",\"verdict\":\"PORT: BESTÅET\"", ""));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.NOT_COMPLETED);
    }

    @Test
    void thePortLineIsReadWhateverItsCaseAndSpacing() {
        // Bug: a strict string match turns a correct pass into "no verdict" over formatting.
        model.answer(passing().replace("PORT: BESTÅET", "  port:\\tbestået "));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.PASSED);
    }

    @Test
    void aFailedPointIsAFailWhateverTheWordingOfThePortLine() {
        // Bug: "PORT: DUMPET." or "PORT: DUMPET (punkt 13)" does not match a whole-string pattern,
        // the answer is discarded, no rewrite runs, and an echo letter ships as "no verdict".
        model.answer(answer(point1(true, 9, 14), plain(8, true),
                plain(13, false, "I søger en, der kan X, Y og Z."), point14(true, "[1]", 0), "PORT: DUMPET."));

        LetterGateResult result = judge();

        assertThat(result.verdict()).isEqualTo(LetterGateVerdict.FAILED);
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void aPassNeedsAPortLineThatSaysPassedAndNothingElse() {
        // Bug: the template's own "PORT: BESTÅET | PORT: DUMPET" copied back is read as a pass;
        // or trailing punctuation turns a real pass into "no verdict".
        model.answer(passing().replace("PORT: BESTÅET", "PORT: BESTÅET | PORT: DUMPET"))
                .answer(passing().replace("PORT: BESTÅET", "PORT: BESTÅET."));

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.PASSED);
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void anUnreadableAnswerIsAskedOnceMoreThenReportedAsNotCompleted() {
        // Bug: an answer we cannot parse is stored as a pass, or as a fail with nothing quoted,
        // which would spend a rewrite with nothing to act on.
        model.answer("Ansøgningen ser fin ud.").answer("{\"verdict\":\"PORT: BESTÅET\"}");

        LetterGateResult result = judge();

        assertThat(result.verdict()).isEqualTo(LetterGateVerdict.NOT_COMPLETED);
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void aSecondReadableAnswerIsUsed() {
        // Bug: one unreadable answer ends the gate although a retry would have given a verdict.
        model.answer("not json").answer(passing());

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.PASSED);
    }

    @Test
    void aProviderErrorIsNoVerdict() {
        // Bug: a provider error escapes and fails the whole generation, losing a finished letter.
        model.answer("THROW");

        assertThat(judge().verdict()).isEqualTo(LetterGateVerdict.NOT_COMPLETED);
    }

    @Test
    void theGateSeesOnlyTheLetterThePostingAndTheChecklist() {
        // Bug: the gate is handed the drafter's reasoning or the needs, and judges the letter by
        // its intentions instead of its text.
        model.answer(passing());

        judge();

        PromptComposition prompt = model.prompts.get(0);
        assertThat(prompt.resolvedFinalPrompt()).contains(LETTER, POSTING, "OPSLAGS-EKKO", "CV-REFERAT",
                "FARVETESTEN", "FREMAD vs. BAGUD");
        assertThat(prompt.resolvedFinalPrompt()).doesNotContain("Employer Needs");
        assertThat(prompt.systemPrompt()).contains("Untrusted Input");
    }
}
