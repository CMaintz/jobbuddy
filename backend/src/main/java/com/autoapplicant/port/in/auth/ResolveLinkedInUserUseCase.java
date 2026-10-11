package com.autoapplicant.port.in.auth;

import com.autoapplicant.domain.user.User;

public interface ResolveLinkedInUserUseCase {
  /**
   * Resolves the internal user for a LinkedIn OAuth login by its LinkedIn id, or creates a new one
   * when the email is free.
   *
   * @throws SecurityException when no account is linked to the LinkedIn id and LinkedIn has not
   *     verified the email
   * @throws com.autoapplicant.domain.common.AccountExistsException when no account is linked to the
   *     LinkedIn id but another account already holds the email
   */
  User resolve(LinkedInIdentity identity);
}
