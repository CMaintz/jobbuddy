package com.autoapplicant.adapter.security;

import com.autoapplicant.port.out.security.IdentityProviderPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The desktop profile has no sign-in provider: its one user exists only in the local database, so
 * no account is held anywhere else to look up or delete.
 */
@Component
@Profile("desktop")
public class LocalIdentityAdapter implements IdentityProviderPort {

  @Override
  public boolean emailInUse(String email) {
    return false;
  }

  @Override
  public void provisionVerifiedAccount(String uid, String email, String displayName) {
    throw new IllegalStateException("The desktop profile has no sign-in provider to create accounts in");
  }

  @Override
  public void deleteAccount(String uid) {
    // Nothing is held outside the local database.
  }
}
