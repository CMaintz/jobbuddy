package com.autoapplicant.domain.ai;

/**
 * Per-dimension fit scores for a CV-vs-job analysis (0-100 each).
 * Location is an unweighted veto, not a score: PASS, FLAG or FAIL.
 */
public record AnalysisDimensions(
        Integer technicalSkills,
        Integer experience,
        Integer cultureFit,
        Integer careerAlignment,
        String location,
        String locationNote
) {
    /** Weighted overall: technical 30 %, experience 25 %, culture 15 %, career 30 %. */
    public int weightedScore() {
        return Math.round(
                nz(technicalSkills) * 0.30f
                + nz(experience) * 0.25f
                + nz(cultureFit) * 0.15f
                + nz(careerAlignment) * 0.30f);
    }

    public boolean isComplete() {
        return technicalSkills != null && experience != null
                && cultureFit != null && careerAlignment != null;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : Math.max(0, Math.min(100, v));
    }
}
