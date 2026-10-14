package com.autoapplicant.adapter.security;

import com.autoapplicant.domain.common.AccountExistsException;
import com.autoapplicant.port.out.security.IdentityProviderPort;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Component;

@Component
public class FirebaseIdentityAdapter implements IdentityProviderPort {

  private final FirebaseAuth firebaseAuth;

  public FirebaseIdentityAdapter(FirebaseAuth firebaseAuth) {
    this.firebaseAuth = firebaseAuth;
  }

  @Override
  public boolean emailInUse(String email) {
    try {
      firebaseAuth.getUserByEmail(email);
      return true;
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
        return false;
      }
      throw new IllegalStateException("Could not look up the Firebase account", e);
    }
  }

  @Override
  public void provisionVerifiedAccount(String uid, String email, String displayName) {
    try {
      if (exists(uid)) {
        firebaseAuth.updateUser(
            new UserRecord.UpdateRequest(uid).setEmail(email).setEmailVerified(true));
      } else {
        firebaseAuth.createUser(createRequest(uid, email, displayName));
      }
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
        throw new AccountExistsException();
      }
      throw new IllegalStateException("Could not provision the Firebase account", e);
    }
  }

  private boolean exists(String uid) throws FirebaseAuthException {
    try {
      firebaseAuth.getUser(uid);
      return true;
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
        return false;
      }
      throw e;
    }
  }

  private static UserRecord.CreateRequest createRequest(
      String uid, String email, String displayName) {
    UserRecord.CreateRequest create =
        new UserRecord.CreateRequest().setUid(uid).setEmail(email).setEmailVerified(true);
    if (displayName != null && !displayName.isBlank()) {
      create.setDisplayName(displayName);
    }
    return create;
  }
}
