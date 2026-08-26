package com.autoapplicant.domain.document;

import java.util.List;

/**
 * A deterministic quality score for a generated document — the measurement half of prompt work.
 *
 * <p>Prompt changes were previously judged by reading one output and forming an impression. This
 * makes the judgement repeatable: every dimension is computed model-free, so the same document
 * always scores the same and two prompt revisions can be compared on the same fixtures.
 *
 * @param total      weighted 0–100 overall score
 * @param dimensions per-dimension detail, in declaration order
 */
public record QualityScore(int total, List<Dimension> dimensions) {

    /**
     * @param code   stable identifier for the dimension
     * @param score  0–100, higher is better
     * @param weight relative contribution to {@link #total}
     * @param detail human-readable explanation of the score
     */
    public record Dimension(String code, int score, int weight, String detail) {}

    /** The score for one dimension, or -1 when the evaluator did not produce it. */
    public int scoreOf(String code) {
        return dimensions.stream().filter(d -> d.code().equals(code))
                .mapToInt(Dimension::score).findFirst().orElse(-1);
    }
}
