package com.autoapplicant.domain.common;

/**
 * A sign-in cannot be attached to the account that owns its email. Extends {@link
 * IllegalStateException} so the web layer answers 409.
 */
public class AccountConflictException extends IllegalStateException {

  public AccountConflictException(String message) {
    super(message);
  }
}
