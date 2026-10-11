package com.autoapplicant.domain.common;

/**
 * A CV could not be turned into a profile: no readable text, an AI provider failure, or an AI
 * response that was not the expected JSON. The message is shown to the user as-is, so it must
 * say what to do next and never carry provider or parser internals; those travel in the cause.
 */
public class CvParseException extends RuntimeException {
    public CvParseException(String message) {
        super(message);
    }

    public CvParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
