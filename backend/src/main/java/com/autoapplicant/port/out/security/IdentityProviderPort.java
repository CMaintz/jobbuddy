package com.autoapplicant.port.out.security;

/** The sign-in provider's account store (Firebase), as far as server-side sign-ins need it. */
public interface IdentityProviderPort {

  /**
   * Makes sure a provider account carries {@code email} as a verified address and returns its uid.
   * An existing account that already holds the email wins, so a LinkedIn sign-in lands in the same
   * account as an earlier Google or password sign-up; otherwise {@code preferredUid} is created or
   * updated.
   *
   * @param email an address the caller has already verified out of band
   * @throws com.autoapplicant.domain.common.AccountConflictException when an account with this
   *     email exists but its owner never verified it
   */
  String ensureVerifiedAccount(String preferredUid, String email, String displayName);
}
