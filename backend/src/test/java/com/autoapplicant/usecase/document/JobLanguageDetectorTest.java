package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobLanguageDetectorTest {

    private static final String DANISH_POSTING = """
            Vi søger en dygtig backend-udvikler til vores team i København. Du kommer til at arbejde
            med Java og Spring Boot, og du får ansvaret for at udvikle og vedligeholde vores services.
            Vi tilbyder en uformel arbejdsplads med dygtige kolleger og gode muligheder for faglig
            udvikling. Send din ansøgning hurtigst muligt — vi holder samtaler løbende.""";

    private static final String ENGLISH_POSTING = """
            We are looking for a skilled backend engineer to join our team in Copenhagen. You will work
            with Java and Spring Boot, and you will have the responsibility of building and maintaining
            our services. We offer an informal workplace with skilled colleagues and good opportunities
            for professional growth. Send your application as soon as possible.""";

    @Test
    void detectsDanishPosting() {
        assertThat(JobLanguageDetector.detect(DANISH_POSTING)).isEqualTo(JobLanguageDetector.DANISH);
    }

    @Test
    void detectsEnglishPosting() {
        assertThat(JobLanguageDetector.detect(ENGLISH_POSTING)).isEqualTo(JobLanguageDetector.ENGLISH);
    }

    @Test
    void returnsNullForTooLittleEvidence() {
        assertThat(JobLanguageDetector.detect("Backend Developer, Copenhagen")).isNull();
        assertThat(JobLanguageDetector.detect("")).isNull();
        assertThat(JobLanguageDetector.detect(null)).isNull();
    }

    @Test
    void explicitChoiceWinsOverDetection() {
        assertThat(JobLanguageDetector.resolve("English", DANISH_POSTING)).isEqualTo("English");
        assertThat(JobLanguageDetector.resolve("  ", DANISH_POSTING)).isEqualTo(JobLanguageDetector.DANISH);
    }

    @Test
    void recognisesDanishInEitherSpelling() {
        assertThat(JobLanguageDetector.isDanish("Dansk")).isTrue();
        assertThat(JobLanguageDetector.isDanish("danish")).isTrue();
        assertThat(JobLanguageDetector.isDanish("da")).isTrue();
        assertThat(JobLanguageDetector.isDanish("English")).isFalse();
        assertThat(JobLanguageDetector.isDanish(null)).isFalse();
    }
}
