package com.autoapplicant.domain.document.structured;

/**
 * The letter gate gave no verdict (a provider outage, an unreadable answer). A re-check that ends
 * this way leaves the stored verdict alone: "no verdict" says nothing about the letter, and storing
 * it would throw away the failed points the user is fixing.
 */
public class LetterGateNoVerdictException extends RuntimeException {

    public LetterGateNoVerdictException() {
        super("The letter check gave no verdict");
    }
}
