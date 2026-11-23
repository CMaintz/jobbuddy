package com.autoapplicant.domain.document.structured;

/**
 * Why the letter gate stopped before a pass. A code rather than prose, so the UI can say it in the
 * user's language.
 */
public enum LetterGateStop {
    /** The letter still fails after the last allowed rewrite. */
    OUT_OF_ROUNDS,
    /** Another rewrite could not finish before the request deadline. */
    OUT_OF_TIME,
    /** The rewrite call failed or returned no letter. */
    REWRITE_FAILED,
    /** The gate could not give a verdict on the letter. */
    NO_VERDICT,
    /** The gate gave no verdict on the last rewrite, so the letter before it was kept. */
    REWRITE_NOT_JUDGED
}
