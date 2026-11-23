package com.autoapplicant.domain.document.structured;

import java.util.List;

/**
 * The letter gate's last verdict on a letter.
 *
 * @param verdict the overall result
 * @param points the result per checklist point; empty when the gate gave no verdict
 * @param rounds rewrite rounds spent before this verdict
 * @param stop why the gate stopped short of a pass; null when it passed
 */
public record LetterGateResult(LetterGateVerdict verdict, List<LetterGatePoint> points, int rounds,
                               LetterGateStop stop) {

    public LetterGateResult {
        points = points != null ? List.copyOf(points) : List.of();
    }

    public boolean passed() {
        return verdict == LetterGateVerdict.PASSED;
    }

    public List<LetterGatePoint> failedPoints() {
        return points.stream().filter(p -> !p.passed()).toList();
    }

    /** This verdict after {@code spentRounds} rewrites, stopped for {@code reason}. */
    public LetterGateResult after(int spentRounds, LetterGateStop reason) {
        return new LetterGateResult(verdict, points, spentRounds, passed() ? null : reason);
    }
}
