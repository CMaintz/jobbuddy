package com.autoapplicant.domain.document.structured;

/** The outcome of the letter gate. */
public enum LetterGateVerdict {
    /** All four points passed and the gate's final line read PORT: BESTÅET. */
    PASSED,
    /** At least one point failed on the last letter the gate judged. */
    FAILED,
    /** The gate could not give a verdict: an unreadable answer or a provider error. */
    NOT_COMPLETED
}
