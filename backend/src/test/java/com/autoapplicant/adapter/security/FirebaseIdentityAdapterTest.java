package com.autoapplicant.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.common.AccountConflictException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FirebaseIdentityAdapterTest {

  FirebaseAuth firebaseAuth;
  FirebaseIdentityAdapter adapter;

  @BeforeEach
  void setUp() {
    firebaseAuth = mock(FirebaseAuth.class);
    adapter = new FirebaseIdentityAdapter(firebaseAuth);
  }

  @Test
  void creates_a_verified_account_for_a_new_member() throws FirebaseAuthException {
    FirebaseAuthException notFound = error(AuthErrorCode.USER_NOT_FOUND);
    when(firebaseAuth.getUserByEmail("new@example.com")).thenThrow(notFound);
    when(firebaseAuth.getUser("linkedin:s")).thenThrow(notFound);
    UserRecord created = record("linkedin:s", true);
    when(firebaseAuth.createUser(any())).thenReturn(created);

    String uid = adapter.ensureVerifiedAccount("linkedin:s", "new@example.com", "New");

    assertThat(uid).isEqualTo("linkedin:s");
  }

  @Test
  void adds_the_verified_email_to_an_existing_custom_token_account() throws FirebaseAuthException {
    FirebaseAuthException notFound = error(AuthErrorCode.USER_NOT_FOUND);
    when(firebaseAuth.getUserByEmail("m@example.com")).thenThrow(notFound);
    UserRecord legacy = record("linkedin:s", false);
    when(firebaseAuth.getUser("linkedin:s")).thenReturn(legacy);

    String uid = adapter.ensureVerifiedAccount("linkedin:s", "m@example.com", "M");

    assertThat(uid).isEqualTo("linkedin:s");
    verify(firebaseAuth).updateUser(any());
    verify(firebaseAuth, never()).createUser(any());
  }

  @Test
  void signs_into_the_verified_account_that_already_owns_the_email()
      throws FirebaseAuthException {
    UserRecord google = record("google-uid", true);
    when(firebaseAuth.getUserByEmail("g@example.com")).thenReturn(google);

    String uid = adapter.ensureVerifiedAccount("linkedin:s", "g@example.com", "G");

    assertThat(uid).isEqualTo("google-uid");
    verify(firebaseAuth, never()).createUser(any());
    verify(firebaseAuth, never()).updateUser(any());
  }

  @Test
  void refuses_an_account_whose_owner_never_verified_the_email() throws FirebaseAuthException {
    UserRecord squatter = record("pw-uid", false);
    when(firebaseAuth.getUserByEmail("v@example.com")).thenReturn(squatter);

    assertThatThrownBy(() -> adapter.ensureVerifiedAccount("linkedin:s", "v@example.com", "V"))
        .isInstanceOf(AccountConflictException.class);
  }

  @Test
  void retries_when_a_concurrent_sign_in_created_the_account_first()
      throws FirebaseAuthException {
    UserRecord winner = record("linkedin:s", true);
    FirebaseAuthException notFound = error(AuthErrorCode.USER_NOT_FOUND);
    when(firebaseAuth.getUserByEmail("r@example.com")).thenThrow(notFound).thenReturn(winner);
    when(firebaseAuth.getUser("linkedin:s")).thenThrow(notFound);
    FirebaseAuthException exists = error(AuthErrorCode.UID_ALREADY_EXISTS);
    when(firebaseAuth.createUser(any())).thenThrow(exists);

    assertThat(adapter.ensureVerifiedAccount("linkedin:s", "r@example.com", "R"))
        .isEqualTo("linkedin:s");
  }

  private static UserRecord record(String uid, boolean emailVerified) {
    UserRecord record = mock(UserRecord.class);
    when(record.getUid()).thenReturn(uid);
    when(record.isEmailVerified()).thenReturn(emailVerified);
    return record;
  }

  private static FirebaseAuthException error(AuthErrorCode code) {
    FirebaseAuthException e = mock(FirebaseAuthException.class);
    when(e.getAuthErrorCode()).thenReturn(code);
    return e;
  }
}
