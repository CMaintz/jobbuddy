package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DocumentFactGuardTest {

    private final DocumentFactGuard guard = new DocumentFactGuard();

    private static final String SOURCE = String.join(" ",
            "Reached 16,181 active users and 289,760 enrollments across 80 courses.",
            "Cut infrastructure cost 60%. Managed a $550K budget.",
            "Certified partners earned 2x more. Authored 80+ open-access technical guides.");

    @Test
    void truthfulMetricPasses() {
        assertThat(guard.audit("Reached 16,181 users", SOURCE).clean()).isTrue();
    }

    @Test
    void inflatedCountIsCaught() {
        assertThat(guard.audit("Reached 94,772 active users", SOURCE).inventedMetrics())
                .containsExactly("94,772 users");
    }

    @Test
    void newMetricNounIsCaught() {
        assertThat(guard.audit("Drove 900,000 enrollments", SOURCE).inventedMetrics())
                .containsExactly("900,000 enrollments");
    }

    @Test
    void truthfulCurrencyPassesAndInflatedIsCaught() {
        assertThat(guard.audit("Managed a $550K budget", SOURCE).clean()).isTrue();
        // Reported in the document's own spelling, not the normalized comparison form.
        assertThat(guard.audit("Managed a $900K budget", SOURCE).inventedMetrics())
                .containsExactly("$900K");
    }

    @Test
    void truthfulPercentAndMultiplierPass() {
        assertThat(guard.audit("Cut cost 60%", SOURCE).clean()).isTrue();
        assertThat(guard.audit("Partners earned 2x more", SOURCE).clean()).isTrue();
    }

    @Test
    void nounSynonymPasses() {
        assertThat(guard.audit("Authored 80 articles", SOURCE).clean()).isTrue();
    }

    @Test
    void aRewrittenMagnitudeIsTheSameClaim() {
        // The most common way a model rewrites a number without changing it.
        assertThat(guard.audit("Grew to 50k users", "Reached 50,000 users").clean()).isTrue();
        assertThat(guard.audit("Grew to 50,000 users", "Reached 50k users").clean()).isTrue();
    }

    @Test
    void aTranslatedNounIsTheSameClaim() {
        // The profile is often English while the letter is Danish, or the reverse.
        assertThat(guard.audit("Nåede 16.000 brugere", "Reached 16,000 users").clean()).isTrue();
        assertThat(guard.audit("Reached 16,000 users", "Nåede 16.000 brugere").clean()).isTrue();
        assertThat(guard.audit("3 years of experience", "3 års erfaring").clean()).isTrue();
        // …and a Danish fabrication is still caught against an English profile.
        assertThat(guard.audit("Nåede 90.000 brugere", "Reached 16,000 users").inventedMetrics())
                .isNotEmpty();
    }

    @Test
    void aSpelledOutNumberIsTheSameClaim() {
        assertThat(guard.audit("30 services", "thirty services").clean()).isTrue();
        assertThat(guard.audit("30 tjenester", "tredive tjenester").clean()).isTrue();
    }

    @Test
    void aNumberWordNotCountingAnythingIsLeftAlone() {
        // Folding every "one" would turn "one of the teams" into a claim that was never made.
        assertThat(guard.audit("one of the teams shipped it", "worked with the team").clean()).isTrue();
    }

    @Test
    void aFigureCountingSomethingElseIsUnverifiedRatherThanInvented() {
        var audit = guard.audit("Ran 80 teams", SOURCE);
        assertThat(audit.inventedMetrics()).isEmpty();
        assertThat(audit.unverifiedMetrics()).containsExactly("80 teams");
        assertThat(audit.clean()).isFalse();
    }

    @Test
    void danishCurrencyIsCheckedAtAll() {
        String source = "Forvaltede et budget på 550.000 kr.";
        // Same amount, three spellings the two languages use between them.
        assertThat(guard.audit("budget på 550.000 kr.", source).clean()).isTrue();
        assertThat(guard.audit("budget på 550.000 kroner", source).clean()).isTrue();
        assertThat(guard.audit("a DKK 550.000 budget", source).clean()).isTrue();
        // …and an inflated one no longer slips through unchecked.
        assertThat(guard.audit("budget på 900.000 kr.", source).inventedMetrics()).isNotEmpty();
    }

    @Test
    void anExpandedAbbreviationIsNotAFabrication() {
        // People type "40 min"; a model writing it out as "40 minutes" has invented nothing.
        assertThat(guard.audit("Deploys went from 40 minutes to 9", "deploys went from 40 min to 9")
                .clean()).isTrue();
        assertThat(guard.audit("3 years of it", "3 yrs of it").clean()).isTrue();
        // …and the check still bites when the number itself changed.
        assertThat(guard.audit("Deploys went from 90 minutes to 9", "deploys went from 40 min to 9")
                .inventedMetrics()).isNotEmpty();
    }

    @Test
    void ordinaryYearIsIgnored() {
        assertThat(guard.audit("Joined the team in 2013", SOURCE).clean()).isTrue();
    }

    @Test
    void inflatedMagnitudeSuffixIsCaught() {
        assertThat(guard.audit("Grew the product to 50k users", "Reached 50 users.").inventedMetrics())
                .containsExactly("50k users");
    }

    @Test
    void magnitudeSuffixSupportedBySourcePasses() {
        assertThat(guard.audit("Grew to 50k users", "Reached 50k users.").clean()).isTrue();
    }

    @Test
    void thousandsGroupingStyleDoesNotDecideMatch() {
        // period-grouped document vs comma-grouped source describe the same number
        assertThat(guard.audit("Reached 16.181 users", SOURCE).clean()).isTrue();
        assertThat(guard.audit("Reached 16 181 users", SOURCE).clean()).isTrue();
    }

    private static final String COMMS_SOURCE = String.join(" ",
            "Grew the LinkedIn page to 12,000 followers.",
            "Ran 8 campaigns with 450,000 impressions.");

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Vi voksede til 10.000 følgere på LinkedIn.   | 10.000 følgere",
            "Jeg stod for 25 kampagner om året.           | 25 kampagner",
            "Opslagene fik 900.000 visninger.             | 900.000 visninger",
            "Nyhedsbrevet fik 3.000 nye abonnenter.       | 3.000 abonnenter",
            "We grew the page to 10,000 followers.        | 10,000 followers",
            "I ran 25 campaigns a year.                   | 25 campaigns",
            "The posts reached 900,000 impressions.       | 900,000 impressions",
            "The newsletter gained 3,000 subscribers.     | 3,000 subscribers",
            "The campaign generated 400 leads.            | 400 leads"})
    void anInventedCommsCountIsCaught(String sentence, String claim) {
        assertThat(guard.audit(sentence, COMMS_SOURCE).inventedMetrics()).containsExactly(claim);
    }

    @Test
    void supportedCommsCountsPassInEitherLanguageBesideAnInventedOne() {
        DocumentFactGuard.FactAudit english = guard.audit(
                "I grew the page to 12,000 followers across 8 campaigns and 40 posts.",
                COMMS_SOURCE);
        assertThat(english.inventedMetrics()).containsExactly("40 posts");
        assertThat(english.unverifiedMetrics()).isEmpty();

        // The profile is English and the letter Danish: the same figures, translated.
        DocumentFactGuard.FactAudit danish = guard.audit(
                "Jeg voksede siden til 12.000 følgere gennem 8 kampagner med 450.000 visninger "
                        + "og 40 opslag.", COMMS_SOURCE);
        assertThat(danish.inventedMetrics()).containsExactly("40 opslag");
        assertThat(danish.unverifiedMetrics()).isEmpty();
    }

    @Test
    void theEnglishPrepositionToIsNotTheDanishNumberTwo() {
        assertThat(guard.audit("I presented the strategy to employees and to customers.",
                COMMS_SOURCE).clean()).isTrue();
        // Danish "to" before a Danish noun is still the number two.
        assertThat(guard.audit("Jeg ledte to medarbejdere.", COMMS_SOURCE).inventedMetrics())
                .containsExactly("2 medarbejdere");
        // Other Danish number words are not English words, so they still count any noun,
        // including the English loan nouns Danish tech text uses.
        assertThat(guard.audit("I led 3 teams.", "Ledte tre teams.").clean()).isTrue();
        assertThat(guard.audit("I moved 3 services.", "Migrerede tre services.").clean()).isTrue();
    }

    @Test
    void aCountBelongsToTheNearestNounNotOneFurtherAlong() {
        // "reach", "leads" and "posts" are also verbs; a later one must not steal the count.
        assertThat(guard.audit("I ran 8 campaigns to reach new audiences and generate leads.",
                COMMS_SOURCE).clean()).isTrue();
        assertThat(guard.audit("Jeg kørte 8 kampagner for at skabe leads.", COMMS_SOURCE).clean())
                .isTrue();
    }
}
