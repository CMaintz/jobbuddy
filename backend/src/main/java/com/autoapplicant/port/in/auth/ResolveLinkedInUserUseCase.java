package com.autoapplicant.port.in.auth;

import com.autoapplicant.domain.user.User;

public interface ResolveLinkedInUserUseCase {
  /**
   * Resolves (or creates) an internal user record for a LinkedIn OAuth login. Tries to match by
   * linkedinId first, then by email, and finally creates a new user.
   *
   * @throws SecurityException when no account is linked to the LinkedIn id yet and LinkedIn has
   *     not verified the email, since linking or creating on it would let anyone claim an address
   */
  User resolve(LinkedInIdentity identity);
}
