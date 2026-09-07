package com.autoapplicant.domain.matching;

public enum MatchLabel {
    EXCELLENT,
    STRONG,
    MODERATE,
    WEAK;

    public static MatchLabel fromScore(int score) {
        if (score >= 80) return EXCELLENT;
        if (score >= 60) return STRONG;
        if (score >= 40) return MODERATE;
        return WEAK;
    }
}
