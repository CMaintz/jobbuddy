package com.autoapplicant.usecase.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketConventionsTest {

    @Test
    void countryWinsOverLanguage() {
        assertThat(MarketConventions.resolve("English", "Denmark")).isEqualTo(MarketConventions.Market.DENMARK);
        assertThat(MarketConventions.resolve("Danish", "Germany")).isEqualTo(MarketConventions.Market.GENERIC);
    }

    @Test
    void languageDecidesWhenCountryIsUnknown() {
        assertThat(MarketConventions.resolve("Danish", null)).isEqualTo(MarketConventions.Market.DENMARK);
        assertThat(MarketConventions.resolve("Dansk", "  ")).isEqualTo(MarketConventions.Market.DENMARK);
        assertThat(MarketConventions.resolve("English", null)).isEqualTo(MarketConventions.Market.GENERIC);
        assertThat(MarketConventions.resolve(null, null)).isEqualTo(MarketConventions.Market.GENERIC);
    }

    @Test
    void danishCountrySpellingsAreRecognised() {
        assertThat(MarketConventions.resolve(null, "Danmark")).isEqualTo(MarketConventions.Market.DENMARK);
        assertThat(MarketConventions.resolve(null, "DK")).isEqualTo(MarketConventions.Market.DENMARK);
    }

    @Test
    void genericMarketContributesNoRules() {
        assertThat(MarketConventions.letterRules(MarketConventions.Market.GENERIC)).isEmpty();
        assertThat(MarketConventions.cvRules(MarketConventions.Market.GENERIC)).isEmpty();
    }

    @Test
    void danishRulesCoverTheTwoMostCitedRejectionReasons() {
        String letter = MarketConventions.letterRules(MarketConventions.Market.DENMARK);
        assertThat(letter).contains("Jeg søger hermed stillingen")   // formulaic opener
                .contains("retell the CV");                          // CV-retelling trap
        assertThat(MarketConventions.cvRules(MarketConventions.Market.DENMARK))
                .contains("two A4 pages");
    }
}
