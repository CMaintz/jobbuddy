package com.autoapplicant.domain.common;

/**
 * The record does not exist, or it belongs to another user. The two are deliberately
 * indistinguishable so a caller cannot probe for other users' ids.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
