package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.usecase.document.GenerationGuardrails.Medium;
import org.junit.jupiter.api.Test;

/**
 * The medium mapping is the single reconciliation point between the app's document-type vocabulary
 * and the framing register: one declaration ({@link Medium#forDocumentType}) drives both the prompt
 * envelope and the output-guard applicability, so a surface cannot carry one without the other.
 */
class GenerationGuardrailsTest {

    @Test
    void mapsDocumentTypesOntoTheirFramingRegister() {
        assertThat(Medium.forDocumentType("COVER_LETTER")).isEqualTo(Medium.LETTER);
        assertThat(Medium.forDocumentType("APPLICATION_TEXT")).isEqualTo(Medium.LETTER);
        assertThat(Medium.forDocumentType("UNSOLICITED_APPLICATION")).isEqualTo(Medium.LETTER);
        assertThat(Medium.forDocumentType("RECRUITER_MESSAGE")).isEqualTo(Medium.OUTREACH);
        assertThat(Medium.forDocumentType("FOLLOW_UP_MESSAGE")).isEqualTo(Medium.OUTREACH);
        assertThat(Medium.forDocumentType("CV")).isEqualTo(Medium.CV);
        assertThat(Medium.forDocumentType("CV_TAILORING")).isEqualTo(Medium.CV);
        assertThat(Medium.forDocumentType("CV_ANALYSIS")).isEqualTo(Medium.ANALYSIS);
    }

    @Test
    void unknownOrAbsentTypeDefaultsToLetterTheSafeWritingRegister() {
        assertThat(Medium.forDocumentType(null)).isEqualTo(Medium.LETTER);
        assertThat(Medium.forDocumentType("something-new")).isEqualTo(Medium.LETTER);
        assertThat(Medium.forDocumentType(" cv ")).isEqualTo(Medium.CV); // trimmed + case-insensitive
    }

    @Test
    void onlyDeliveredDocumentMediaAreWritingMedia() {
        assertThat(Medium.LETTER.writesDeliveredDocument()).isTrue();
        assertThat(Medium.CV.writesDeliveredDocument()).isTrue();
        assertThat(Medium.OUTREACH.writesDeliveredDocument()).isTrue();
        assertThat(Medium.ANALYSIS.writesDeliveredDocument()).isFalse();
        assertThat(Medium.INTERVIEW.writesDeliveredDocument()).isFalse();
    }

    @Test
    void writingMediaCarryHonestyAndBannedPhraseBlocksReadingMediaDoNot() {
        GenerationGuardrails letter = GenerationGuardrails.forDocument("COVER_LETTER", null, "job", "DK");
        assertThat(letter.honestyRules()).isNotBlank();
        assertThat(letter.clicheBlock()).isNotBlank();

        GenerationGuardrails analysis = GenerationGuardrails.forDocument("CV_ANALYSIS", null, "job", "DK");
        assertThat(analysis.honestyRules()).isBlank();
        assertThat(analysis.clicheBlock()).isBlank();
    }

    @Test
    void forDocumentRoutesCvReviewThroughCvConventionsNotLetterConventions() {
        // The bug this closes: a CV review was framed with letter market rules. forDocument keys the
        // envelope off the document type, so a "CV" review resolves the same rules as the CV medium.
        GenerationGuardrails cvReview =
                GenerationGuardrails.forDocument("CV", "Danish", "dansk jobopslag", "DK");
        GenerationGuardrails cvMedium =
                GenerationGuardrails.forMedium(Medium.CV, "Danish", "dansk jobopslag", "DK");
        GenerationGuardrails letterMedium =
                GenerationGuardrails.forMedium(Medium.LETTER, "Danish", "dansk jobopslag", "DK");

        assertThat(cvReview.marketRules()).isEqualTo(cvMedium.marketRules());
        assertThat(cvReview.marketRules()).isNotEqualTo(letterMedium.marketRules());
    }
}
