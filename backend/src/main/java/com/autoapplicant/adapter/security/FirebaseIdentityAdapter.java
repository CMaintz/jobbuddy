package com.autoapplicant.adapter.security;

import com.autoapplicant.domain.common.AccountConflictException;
import com.autoapplicant.port.out.security.IdentityProviderPort;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FirebaseIdentityAdapter implements IdentityProviderPort {

  private final FirebaseAuth firebaseAuth;

  public FirebaseIdentityAdapter(FirebaseAuth firebaseAuth) {
    this.firebaseAuth = firebaseAuth;
  }

  @Override
  public String ensureVerifiedAccount(String preferredUid, String email, String displayName) {
    try {
      return ensure(preferredUid, email, displayName);
    } catch (FirebaseAuthException e) {
      if (!isAlreadyExists(e)) {
        throw new IllegalStateException("Could not provision the Firebase account", e);
      }
      // A concurrent first sign-in created the account between our lookup and create: the
      // second pass finds it.
      try {
        return ensure(preferredUid, email, displayName);
      } catch (FirebaseAuthException retry) {
        throw new IllegalStateException("Could not provision the Firebase account", retry);
      }
    }
  }

  private String ensure(String preferredUid, String email, String displayName)
      throws FirebaseAuthException {
    Optional<UserRecord> byEmail = find(() -> firebaseAuth.getUserByEmail(email));
    if (byEmail.isPresent()) {
      return verifiedOwner(byEmail.get());
    }
    if (find(() -> firebaseAuth.getUser(preferredUid)).isPresent()) {
      firebaseAuth.updateUser(
          new UserRecord.UpdateRequest(preferredUid).setEmail(email).setEmailVerified(true));
      return preferredUid;
    }
    UserRecord.CreateRequest create =
        new UserRecord.CreateRequest().setUid(preferredUid).setEmail(email).setEmailVerified(true);
    if (displayName != null && !displayName.isBlank()) {
      create.setDisplayName(displayName);
    }
    return firebaseAuth.createUser(create).getUid();
  }

  /**
   * Someone may have registered this address without ever proving they own it. Handing that
   * account to the verified LinkedIn member would let the registrant keep a password into it.
   */
  private String verifiedOwner(UserRecord account) {
    if (!account.isEmailVerified()) {
      throw new AccountConflictException(
          "An account with this email exists but its email is not verified");
    }
    return account.getUid();
  }

  private Optional<UserRecord> find(Lookup lookup) throws FirebaseAuthException {
    try {
      return Optional.of(lookup.get());
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private static boolean isAlreadyExists(FirebaseAuthException e) {
    return e.getAuthErrorCode() == AuthErrorCode.UID_ALREADY_EXISTS
        || e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS;
  }

  @FunctionalInterface
  private interface Lookup {
    UserRecord get() throws FirebaseAuthException;
  }
}
