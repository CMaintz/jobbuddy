package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fact guard's contract, as two lists.
 *
 * <p>The guard exists to catch a fabricated number before it reaches an employer, because that is
 * the one error a candidate cannot talk their way out of in an interview. It is worth keeping only
 * if it does that without crying wolf — a check that flags truthful sentences gets ignored, and
 * then it is not protecting anything.
 *
 * <p>So the contract is stated here rather than argued about: MUST_CATCH is what makes the guard
 * worth having, MUST_NOT_FLAG is what makes it bearable. Both lists come from real behaviour —
 * every MUST_NOT_FLAG case failed at some point.
 *
 * <p>A new normalization rule is only safe if both lists stay green. Loosening the guard to fix a
 * false positive tends to punch a hole in MUST_CATCH, and this is where that shows up.
 */
class DocumentFactGuardContractTest {

    private final DocumentFactGuard guard = new DocumentFactGuard();

    /** A realistic contact-free profile, as CareerProfileContextService renders one. */
    private static final String PROFILE = """
            {"headline":"Platform engineer",
             "skills":["Kubernetes","Terraform"],
             "experience":[
               {"title":"Platform engineer","subtitle":"Netcompany","dateRange":"Jan 2021 - Mar 2024",
                "description":"Owned the Terraform estate for 30 AWS accounts.",
                "bullets":["Cut median deploy time from 40 min to 9","Ran 16,000 monthly requests"]},
               {"title":"Developer","subtitle":"Acme","dateRange":"Aug 2019 - Dec 2020",
                "bullets":["Managed a $550K budget","Supported 12 teams"]}],
             "proofPoints":["Kubernetes — moved 30 services onto a shared cluster."]}""";

    private record Case(String name, String sentence) {}

    /** Fabrications. Every one of these must be reported as invented, not merely unverified. */
    private static final List<Case> MUST_CATCH = List.of(
            new Case("a number from nowhere", "We reached 94,772 users last year."),
            new Case("an inflated count", "I ran 900 services in production."),
            new Case("an inflated magnitude", "The platform grew to 50k users."),
            new Case("an invented budget", "I managed a $2M budget."),
            new Case("an invented Danish figure", "Jeg nåede 90.000 brugere."),
            new Case("an invented DKK amount", "Jeg forvaltede et budget på 900.000 kr."),
            new Case("a duration far beyond the profile", "I have 20 years of experience."),
            new Case("an invented percentage", "I cut costs by 73%."));

    /**
     * Truthful sentences, phrased the way a model actually phrases them. Any of these being
     * flagged is the guard failing at its job, not the writer failing at theirs.
     */
    private static final List<Case> MUST_NOT_FLAG = List.of(
            new Case("verbatim", "I ran 30 AWS accounts."),
            new Case("magnitude rewritten", "We handled 16k monthly requests."),
            new Case("thousands separator dropped", "We handled 16000 monthly requests."),
            new Case("abbreviation expanded", "Deploy time fell from 40 minutes to 9."),
            new Case("noun translated to Danish", "Jeg flyttede 30 tjenester."),
            new Case("Danish count with Danish separators", "Vi håndterede 16.000 anmodninger."),
            new Case("number spelled out", "I ran thirty services."),
            new Case("Danish number spelled out", "Jeg kørte tredive tjenester."),
            new Case("duration implied by one role", "I spent three years at Netcompany."),
            new Case("duration in Danish", "Jeg har tre års erfaring med platformsarbejde."),
            new Case("duration rounded up", "Nearly four years on the platform team."),
            new Case("total experience across roles", "I have 5 years of experience."),
            new Case("months rather than years", "38 months on the platform team."),
            new Case("a bare number counting nothing", "One of the teams shipped it."),
            new Case("a year, not a count", "I joined the team in 2021."),
            new Case("currency in the other notation", "I managed a 550k budget."));

    @TestFactory
    Stream<DynamicTest> everyFabricationIsCaught() {
        return MUST_CATCH.stream().map(c -> DynamicTest.dynamicTest(c.name(), () ->
                assertThat(guard.audit(c.sentence(), PROFILE).inventedMetrics())
                        .as("\"%s\" must be reported as invented", c.sentence())
                        .isNotEmpty()));
    }

    @TestFactory
    Stream<DynamicTest> noTruthfulSentenceIsFlagged() {
        return MUST_NOT_FLAG.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            var audit = guard.audit(c.sentence(), PROFILE);
            assertThat(audit.inventedMetrics())
                    .as("\"%s\" is true of the profile and must not be called invented", c.sentence())
                    .isEmpty();
            assertThat(audit.unverifiedMetrics())
                    .as("\"%s\" is true of the profile and must not be queried either", c.sentence())
                    .isEmpty();
        }));
    }

    @Test
    void theContractIsMeasuredAsPrecisionAndRecall() {
        long caught = MUST_CATCH.stream()
                .filter(c -> !guard.audit(c.sentence(), PROFILE).inventedMetrics().isEmpty()).count();
        long falsePositives = MUST_NOT_FLAG.stream()
                .filter(c -> !guard.audit(c.sentence(), PROFILE).clean()).count();

        System.out.printf("fact guard: recall %d/%d, false positives %d/%d%n",
                caught, MUST_CATCH.size(), falsePositives, MUST_NOT_FLAG.size());

        // Recall is allowed to be imperfect — the guard is a net, not a proof. Precision is not:
        // one false accusation costs more trust than one missed number costs safety, because the
        // reviewer pass and the honesty rules are also looking for fabrications and this is not.
        assertThat(caught).isEqualTo(MUST_CATCH.size());
        assertThat(falsePositives).isZero();
    }

    @Test
    void aFigureAttachedToTheWrongThingIsQueriedNotAccused() {
        // The middle case the two lists do not cover: the number is real, the noun is not.
        var audit = guard.audit("I supported 30 teams.", PROFILE);
        assertThat(audit.inventedMetrics()).isEmpty();
        assertThat(audit.unverifiedMetrics()).containsExactly("30 teams");
    }
}
