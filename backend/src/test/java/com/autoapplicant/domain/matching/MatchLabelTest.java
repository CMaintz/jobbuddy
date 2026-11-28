package com.autoapplicant.domain.matching;

import static com.autoapplicant.domain.matching.MatchLabel.WEAK;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    void negative_score_returns_weak() {
        assertThat(MatchLabel.fromScore(-1)).isEqualTo(WEAK);
    }
}
