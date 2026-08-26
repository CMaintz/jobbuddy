package com.autoapplicant.usecase.eval;

import com.autoapplicant.domain.document.QualityScore;
import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The precondition for building anything further on evidence elicitation: does recording evidence
 * actually change what the evaluator sees?
 *
 * <p>The mechanism is easy to get wrong in a way that looks fine. `evidence` counts terms the
 * document shares with the PROFILE — so a letter can name a real project and score nothing for it
 * if that project is not in the profile the prompt was given. Eliciting evidence into
 * `interview_story` is therefore only worth anything once those stories reach
 * `CareerProfileForAi.proofPoints`, which is what {@code CareerProfileContextService} now does.
 *
 * <p>These tests hold the letter fixed and change only the profile, which is the honest form of the
 * question: the same words score higher when the profile can back them.
 */
class EvidenceLoopTest {

    private final DocumentQualityEvaluator evaluator =
            new DocumentQualityEvaluator(new ClicheGuard(), new DocumentFactGuard());

    /** The profile as import leaves it: skills listed, nothing behind them. */
    private static final String PROFILE_WITHOUT_EVIDENCE = """
            {"headline":"Platform engineer","skills":["Kubernetes","Terraform"],
             "experience":[{"title":"Platform engineer","subtitle":"Netcompany"}],
             "proofPoints":[]}""";

    /** The same profile after one round of elicitation. */
    private static final String PROFILE_WITH_EVIDENCE = """
            {"headline":"Platform engineer","skills":["Kubernetes","Terraform"],
             "experience":[{"title":"Platform engineer","subtitle":"Netcompany"}],
             "proofPoints":["Kubernetes — moved 30 services onto a shared cluster at Netcompany. \
             Median deploy time went from 40 minutes to 9."]}""";

    /** One letter, citing the elicited work. */
    private static final String LETTER = """
            At Netcompany I moved 30 services onto a shared Kubernetes cluster, and the median \
            deploy time went from 40 minutes to 9 — mostly by deleting the special cases rather \
            than adding tooling.

            That is the same problem your posting describes: shipping safely is a measurement \
            problem before it is a tooling one. Terraform is where I would start, because the \
            environments have to stop being snowflakes before anything else holds.

            I would be glad to talk about where your estate is today.""";

    private static final List<String> POSTING_KEYWORDS = List.of("Kubernetes", "Terraform");

    private QualityScore score(String profileJson) {
        return evaluator.evaluate(LETTER, profileJson, POSTING_KEYWORDS, 160);
    }

    @Test
    void elicitedEvidenceRaisesTheEvidenceDimensionForTheSameLetter() {
        int without = score(PROFILE_WITHOUT_EVIDENCE).scoreOf(DocumentQualityEvaluator.EVIDENCE);
        int with = score(PROFILE_WITH_EVIDENCE).scoreOf(DocumentQualityEvaluator.EVIDENCE);

        System.out.printf("evidence dimension: without=%d with=%d%n", without, with);
        assertThat(with).as("recording evidence must make the letter's citations count")
                .isGreaterThan(without);
    }

    @Test
    void andRaisesTheOverallScore() {
        assertThat(score(PROFILE_WITH_EVIDENCE).total())
                .isGreaterThan(score(PROFILE_WITHOUT_EVIDENCE).total());
    }

    @Test
    void theFigureInTheLetterIsOnlySupportedOnceTheEvidenceExists() {
        // The fact guard reads the same profile: before elicitation, "30 services" is a number the
        // profile cannot back, which is exactly what the guard exists to catch.
        assertThat(score(PROFILE_WITHOUT_EVIDENCE).scoreOf(DocumentQualityEvaluator.FACTS))
                .isLessThan(100);
        assertThat(score(PROFILE_WITH_EVIDENCE).scoreOf(DocumentQualityEvaluator.FACTS))
                .isEqualTo(100);
    }
}
