package com.autoapplicant.domain.matching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.autoapplicant.domain.matching.MatchLabel.*;
import static org.assertj.core.api.Assertions.assertThat;

class MatchLabelTest {

    @ParameterizedTest(name = "score {0} => {1}")
    @CsvSource({
            "100, EXCELLENT",
            "80,  EXCELLENT",
            "79,  STRONG",
            "60,  STRONG",
            "59,  MODERATE",
            "40,  MODERATE",
            "39,  WEAK",
            "1,   WEAK",
            "0,   WEAK"
    })
    void from_score_boundaries(int score, MatchLabel expected) {
        assertThat(MatchLabel.fromScore(score)).isEqualTo(expected);
    }

    @Test
    void score_80_is_excellent_not_strong() {
        assertThat(MatchLabel.fromScore(80)).isEqualTo(EXCELLENT);
        assertThat(MatchLabel.fromScore(80)).isNotEqualTo(STRONG);
    }

    @Test
    void score_60_is_strong_not_moderate() {
        assertThat(MatchLabel.fromScore(60)).isEqualTo(STRONG);
        assertThat(MatchLabel.fromScore(60)).isNotEqualTo(MODERATE);
    }

    @Test
    void score_40_is_moderate_not_weak() {
        assertThat(MatchLabel.fromScore(40)).isEqualTo(MODERATE);
        assertThat(MatchLabel.fromScore(40)).isNotEqualTo(WEAK);
    }

    @Test
    void negative_score_returns_weak() {
        assertThat(MatchLabel.fromScore(-1)).isEqualTo(WEAK);
    }
}
