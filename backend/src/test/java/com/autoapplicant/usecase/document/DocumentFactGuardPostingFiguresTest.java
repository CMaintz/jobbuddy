package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A needs-based letter quotes the posting's own figures back at the employer. Those figures are
 * about the employer, so the profile not holding them is no sign of invention; the same figure
 * claimed by the candidate still is.
 */
class DocumentFactGuardPostingFiguresTest {

    private final DocumentFactGuard guard = new DocumentFactGuard();

    private static final String PROFILE = "Ledte et team på 5 udviklere i 3 år.";

    private static final String POSTING =
            "Kommunen har ca. 680 medarbejdere fordelt på 3 lokationer og betjener 1.200 borgere.";

    @Test
    void theEmployersOwnFigureQuotedBackPasses() {
        String letter = "I har ca. 680 medarbejdere i tre afdelinger, og det kræver overblik.";
        assertThat(audit(letter, POSTING).clean()).isTrue();
    }

    @Test
    void anInventedCandidateFigureStillFlags() {
        String letter = employerSentence("ca. 680 medarbejdere") + " Jeg har ledet 40 medarbejdere.";
        assertThat(audit(letter, POSTING).inventedMetrics())
                .containsExactly("40 medarbejdere");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Jeg har ledet 680 medarbejdere.",
            "Jeg har ledet ca. 680 medarbejdere.",
            "Jeg har ledet\n680 medarbejdere.",
            "Som leder havde vi 680 medarbejdere.",
            "I have managed 680 employees."})
    void aCandidateClaimReusingThePostingsNumberStillFlags(String letter) {
        assertThat(audit(letter, POSTING).inventedMetrics()).hasSize(1);
    }

    @Test
    void anEmployerMentionDoesNotLaunderTheSameFigureClaimedByTheCandidate() {
        String letter = employerSentence("680 medarbejdere") + " Jeg har selv ledet 680 medarbejdere.";
        assertThat(audit(letter, POSTING).inventedMetrics())
                .containsExactly("680 medarbejdere");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Vi har ca. 680 medarbejdere.  | 680 medarbejdere",
            "Vi har 680 medarbejdere.      | ca. 680 medarbejdere",
            "Vi er 1.200 ansatte.          | 1200 ansatte",
            "Vi er 1200 ansatte.           | 1.200 ansatte",
            "Vi er 1.200 ansatte.          | jeres 1.200 medarbejdere"})
    void danishNumberFormattingDoesNotDecideTheMatch(String posting, String figure) {
        assertThat(audit(employerSentence(figure), posting).clean()).isTrue();
    }

    @Test
    void aFigureThatOnlyContainsThePostingsNumberIsNotThePostings() {
        String letter = employerSentence("680 medarbejdere");
        assertThat(audit(letter, "Vi er 1680 medarbejdere.").inventedMetrics())
                .containsExactly("680 medarbejdere");
    }

    @Test
    void thePostingsNumberCountingSomethingElseIsNotThePostingsFigure() {
        String letter = employerSentence("680 projekter");
        assertThat(audit(letter, POSTING).inventedMetrics())
                .containsExactly("680 projekter");
    }

    @Test
    void onlyANumberCountingANounIsExempt() {
        // A percentage has no noun to tie it to the posting's sentence, so the number alone would match.
        String letter = "Jeres kunder fik 20% færre fejl, og det kræver struktur.";
        assertThat(audit(letter, "Stillingen har 20% rejseaktivitet.").inventedMetrics())
                .containsExactly("20%");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Vi søger en med 5 års erfaring.  | Har 5 års erfaring med Java og Spring.",
            "Vi har ca. 680 medarbejdere.     | Ledede 680 medarbejdere gennem en omstilling.",
            "Vi har ca. 680 medarbejdere.     | Det kræver overblik med 680 medarbejdere."})
    void aSentenceThatDoesNotNameTheEmployerStillFlags(String posting, String letter) {
        // A pronoun-less sentence is as likely the candidate's ("Har 5 års erfaring") as the
        // employer's; only a sentence that names the employer may quote the posting unchecked.
        assertThat(audit(letter, posting).clean()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Vi har ca. 680 medarbejdere.  | I har ca. 680 medarbejdere.",
            "Vi er 1.200 ansatte.          | Jeres 1.200 ansatte.",
            "Vi har ca. 680 medarbejdere.  | Kommunen har ca. 680 medarbejdere.",
            "We have 680 employees.        | Your 680 employees need a clear overview."})
    void aSentenceNamingTheEmployerMayQuoteThePosting(String posting, String letter) {
        assertThat(audit(letter, posting).clean()).isTrue();
    }

    @Test
    void theCompanysNameMarksASentenceAsTheEmployers() {
        String letter = "Netcompany har ca. 680 medarbejdere, og det kræver struktur.";
        assertThat(guard.audit(letter, PROFILE, new EmployerPosting(POSTING, "Netcompany")).clean())
                .isTrue();
        assertThat(audit(letter, POSTING).clean()).isFalse();
    }

    private DocumentFactGuard.FactAudit audit(String letter, String posting) {
        return guard.audit(letter, PROFILE, EmployerPosting.of(posting));
    }

    /**
     * Long enough to read as Danish: in a short fragment "I" (you, plural) is indistinguishable
     * from the English first person, and the guard rightly treats it as the candidate.
     */
    private static String employerSentence(String figure) {
        return "Med " + figure + " har I brug for overblik, og det kræver struktur.";
    }
}
