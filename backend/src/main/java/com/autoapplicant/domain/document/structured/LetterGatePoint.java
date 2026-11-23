package com.autoapplicant.domain.document.structured;

import java.util.List;

/**
 * One checklist point of the letter gate.
 *
 * @param point the point's number in the writing guide's checklist (1, 8, 13 or 14)
 * @param passed whether the letter passed it
 * @param quotes the letter's sentences the gate quoted as failing; empty on a pass
 * @param reason the gate's explanation, in the letter's language
 */
public record LetterGatePoint(int point, boolean passed, List<String> quotes, String reason) {

    public LetterGatePoint {
        quotes = quotes != null ? List.copyOf(quotes) : List.of();
    }
}
