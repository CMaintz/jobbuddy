package com.autoapplicant.domain.common;

/**
 * A sign-in would create an account for an email that another account already holds. The web
 * layer answers 409 with {@link #CODE} so the client can tell it apart from other conflicts.
 */
public class AccountExistsException extends RuntimeException {

  public static final String CODE = "ACCOUNT_EXISTS";

  public AccountExistsException() {
    super("An account with this email already exists");
  }
}
