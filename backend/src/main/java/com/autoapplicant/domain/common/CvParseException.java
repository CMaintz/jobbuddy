package com.autoapplicant.domain.common;

/**
 * A CV could not be turned into a profile. The {@link Reason} is the contract a client acts on;
 * the message is a plain-English fallback for clients that do not translate the reason, so it
 * must never carry provider or parser internals. Those travel in the cause.
 */
public class CvParseException extends RuntimeException {

    /** Why the parse failed. The name is sent to clients as a stable code. */
    public enum Reason {
        /** The CV had no readable text, e.g. a scanned PDF. The user can fix this. */
        NO_TEXT,
        /** The AI answered, but not with a usable profile. Retrying may help. */
        UNREADABLE,
        /** The AI provider failed or timed out. Nothing was wrong with the CV. */
        AI_UNAVAILABLE
    }

    private final Reason reason;

    public CvParseException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CvParseException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
