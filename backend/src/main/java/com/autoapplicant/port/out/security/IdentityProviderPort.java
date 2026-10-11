package com.autoapplicant.port.out.security;

/** The sign-in provider's account store (Firebase), as far as server-side sign-ins need it. */
public interface IdentityProviderPort {

  /** True when any provider account already holds this email, whatever the case. */
  boolean emailInUse(String email);

  /**
   * Creates the account {@code uid}, or updates it, so that it carries {@code email} as a verified
   * address. Never touches any other account.
   *
   * @param email an address the caller has already verified out of band
   * @throws com.autoapplicant.domain.common.AccountExistsException when a different account
   *     holds the email
   */
  void provisionVerifiedAccount(String uid, String email, String displayName);
}
