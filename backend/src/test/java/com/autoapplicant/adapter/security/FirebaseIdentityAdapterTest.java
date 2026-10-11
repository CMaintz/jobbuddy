package com.autoapplicant.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.common.AccountExistsException;
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
  void reports_an_email_held_by_any_account() throws FirebaseAuthException {
    UserRecord google = mock(UserRecord.class);
    when(firebaseAuth.getUserByEmail("g@example.com")).thenReturn(google);

    assertThat(adapter.emailInUse("g@example.com")).isTrue();
  }

  @Test
  void reports_a_free_email() throws FirebaseAuthException {
    FirebaseAuthException notFound = error(AuthErrorCode.USER_NOT_FOUND);
    when(firebaseAuth.getUserByEmail("free@example.com")).thenThrow(notFound);

    assertThat(adapter.emailInUse("free@example.com")).isFalse();
  }

  @Test
  void creates_a_verified_account_for_a_new_member() throws FirebaseAuthException {
    FirebaseAuthException notFound = error(AuthErrorCode.USER_NOT_FOUND);
    when(firebaseAuth.getUser("linkedin:s")).thenThrow(notFound);

    adapter.provisionVerifiedAccount("linkedin:s", "new@example.com", "New");

    verify(firebaseAuth).createUser(any());
  }

  @Test
  void adds_the_verified_email_to_its_own_existing_account() throws FirebaseAuthException {
    UserRecord legacy = mock(UserRecord.class);
    when(firebaseAuth.getUser("linkedin:s")).thenReturn(legacy);

    adapter.provisionVerifiedAccount("linkedin:s", "m@example.com", "M");

    verify(firebaseAuth).updateUser(any());
    verify(firebaseAuth, never()).createUser(any());
  }

  @Test
  void refuses_when_another_account_took_the_email() throws FirebaseAuthException {
    FirebaseAuthException notFound = error(AuthErrorCode.USER_NOT_FOUND);
    when(firebaseAuth.getUser("linkedin:s")).thenThrow(notFound);
    FirebaseAuthException taken = error(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    when(firebaseAuth.createUser(any())).thenThrow(taken);

    assertThatThrownBy(() -> adapter.provisionVerifiedAccount("linkedin:s", "t@example.com", "T"))
        .isInstanceOf(AccountExistsException.class);
  }

  private static FirebaseAuthException error(AuthErrorCode code) {
    FirebaseAuthException e = mock(FirebaseAuthException.class);
    when(e.getAuthErrorCode()).thenReturn(code);
    return e;
  }
}
