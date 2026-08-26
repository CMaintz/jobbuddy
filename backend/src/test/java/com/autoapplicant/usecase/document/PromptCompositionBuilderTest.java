package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the market/language wiring of the generation prompts: the blocks that must be present
 * (and absent) for a given posting, since a silently dropped conventions block is invisible in
 * the generated output until a human reads a bad letter.
 */
class PromptCompositionBuilderTest {

    private final PromptCompositionBuilder builder = new PromptCompositionBuilder();

    private static final String DANISH_POSTING = """
            Vi søger en backend-udvikler til vores team i København. Du kommer til at arbejde med
            Java og Spring Boot, og du får ansvaret for vores services. Vi tilbyder en uformel
            arbejdsplads med dygtige kolleger og gode muligheder for udvikling.""";

    private static final String ENGLISH_POSTING = """
            We are looking for a backend engineer to join our team in Berlin. You will work with Java
            and Spring Boot, and you will have the responsibility of building our services. We offer
            an informal workplace with skilled colleagues and good opportunities for growth.""";

    private PromptComposition letter(String posting, String targetLanguage, String country) {
        return builder.composeStructuredApplicationPrompt("COVER_LETTER", "{}", posting, null, null,
                targetLanguage, null, null, List.of(), null, "STANDARD", country);
    }

    private PromptComposition cv(String posting, String targetLanguage, String country) {
        return builder.composeCvTailoringPrompt("{}", posting, null, targetLanguage, null, null,
                List.of(), "STANDARD", country);
    }

    @Test
    void danishPostingGetsDanishMarketRulesAndLanguage() {
        PromptComposition c = letter(DANISH_POSTING, null, null);
        assertThat(c.systemPrompt()).contains("Write the document body in Danish.");
        assertThat(c.userPromptTemplate()).contains("## Danish Market Conventions")
                .contains("Med venlig hilsen");
    }

    @Test
    void nonDanishPostingGetsNoMarketBlock() {
        PromptComposition c = letter(ENGLISH_POSTING, null, null);
        assertThat(c.systemPrompt()).contains("Write the document body in English.");
        assertThat(c.userPromptTemplate()).doesNotContain("## Danish Market Conventions");
    }

    @Test
    void danishEmployerPostingInEnglishStillGetsDanishConventions() {
        // A Copenhagen employer hiring in English is still hiring into the Danish market.
        PromptComposition c = letter(ENGLISH_POSTING, "English", "Denmark");
        assertThat(c.systemPrompt()).contains("Write the document body in English.");
        assertThat(c.userPromptTemplate()).contains("## Danish Market Conventions");
    }

    @Test
    void knownForeignCountryOverridesTheLanguageSignal() {
        PromptComposition c = letter(DANISH_POSTING, null, "Germany");
        assertThat(c.userPromptTemplate()).doesNotContain("## Danish Market Conventions");
    }

    @Test
    void explicitLanguageWinsOverPostingLanguage() {
        assertThat(letter(DANISH_POSTING, "English", null).systemPrompt())
                .contains("Write the document body in English.");
    }

    @Test
    void everyLetterCarriesTheBannedPhraseBlock() {
        assertThat(letter(DANISH_POSTING, null, null).userPromptTemplate())
                .contains("## Banned Phrases")
                .contains("jeg søger hermed stillingen");
    }

    @Test
    void cvPromptCarriesMarketRulesAndBannedPhrases() {
        String prompt = cv(DANISH_POSTING, null, null).userPromptTemplate();
        assertThat(prompt).contains("## Danish Market Conventions")
                .contains("profiltekst")
                .contains("## Banned Phrases");
        assertThat(cv(ENGLISH_POSTING, null, "Germany").userPromptTemplate())
                .doesNotContain("## Danish Market Conventions");
    }

    @Test
    void fixedGuardrailsSurviveEveryPath() {
        for (PromptComposition c : List.of(letter(DANISH_POSTING, null, null), cv(DANISH_POSTING, null, null))) {
            assertThat(c.userPromptTemplate()).contains("## Honesty & ATS Rules", "## Targeting & Proof Rules");
            assertThat(c.systemPrompt()).contains("## Untrusted Input");
        }
    }
}
