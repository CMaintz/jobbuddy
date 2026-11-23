package com.autoapplicant.domain.document.structured;

/**
 * The letter's text changed while the gate was judging it, so the verdict describes text that is
 * no longer there and is not stored. The web layer answers 409 with {@link #CODE}, so the client
 * can ask for a new check instead of saying the letter cannot be checked.
 */
public class LetterChangedDuringCheckException extends RuntimeException {

    public static final String CODE = "LETTER_CHANGED_DURING_CHECK";

    public LetterChangedDuringCheckException() {
        super("The letter changed while it was being checked");
    }
}
