package com.autoapplicant.usecase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.common.AccountExistsException;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.LinkedInIdentity;
import com.autoapplicant.port.out.security.IdentityProviderPort;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LinkedInAuthServiceTest {

  @Mock UserRepositoryPort userRepo;
  @Mock ProfileRepositoryPort profileRepo;
  @Mock ProfilePrivateInfoRepositoryPort privateInfoRepo;
  @Mock IdentityProviderPort identityProvider;

  LinkedInAuthService service;
  UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new LinkedInAuthService(userRepo, profileRepo, privateInfoRepo, identityProvider);
  }

  @Test
  void a_member_linked_by_linkedin_id_still_signs_in() {
    User existing = user("sub1@example.com", "sub1", "fb-1");
    when(userRepo.findByLinkedinId("sub1")).thenReturn(Optional.of(existing));

    User result = service.resolve(verified("sub1", "sub1@example.com", "Sub One"));

    assertThat(result).isSameAs(existing);
    verify(identityProvider).provisionVerifiedAccount("fb-1", "sub1@example.com", "Sub One");
    verify(userRepo, never()).save(any());
    verifyNoInteractions(profileRepo, privateInfoRepo);
  }

  @Test
  void a_legacy_linked_member_gets_a_firebase_account_carrying_the_verified_email() {
    User legacy = user("old@example.com", "sub2", null);
    when(userRepo.findByLinkedinId("sub2")).thenReturn(Optional.of(legacy));
    when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    User result = service.resolve(verified("sub2", "Old@Example.com", "Old"));

    assertThat(result.firebaseUid()).isEqualTo("linkedin:sub2");
    verify(identityProvider).provisionVerifiedAccount("linkedin:sub2", "old@example.com", "Old");
  }

  @Test
  void refuses_an_email_that_an_existing_google_account_holds_and_links_nothing() {
    when(userRepo.findByLinkedinId("sub3")).thenReturn(Optional.empty());
    when(userRepo.findByEmail("victim@example.com"))
        .thenReturn(Optional.of(user("victim@example.com", null, "google-uid")));

    assertThatThrownBy(() -> service.resolve(verified("sub3", "victim@example.com", "Mallory")))
        .isInstanceOf(AccountExistsException.class);

    verify(identityProvider, never()).provisionVerifiedAccount(anyString(), anyString(), any());
    verify(userRepo, never()).save(any());
    verifyNoInteractions(profileRepo, privateInfoRepo);
  }

  @Test
  void refuses_a_case_variant_of_an_email_held_by_a_firebase_account() {
    when(userRepo.findByLinkedinId("sub4")).thenReturn(Optional.empty());
    when(userRepo.findByEmail("Victim@Example.com")).thenReturn(Optional.empty());
    when(identityProvider.emailInUse("Victim@Example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.resolve(verified("sub4", "Victim@Example.com", "M")))
        .isInstanceOf(AccountExistsException.class);

    verify(identityProvider, never()).provisionVerifiedAccount(anyString(), anyString(), any());
    verify(userRepo, never()).save(any());
  }

  @Test
  void creates_a_new_member_with_a_verified_firebase_account_when_the_email_is_free() {
    when(userRepo.findByLinkedinId("sub5")).thenReturn(Optional.empty());
    when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(userRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    User result = service.resolve(verified("sub5", "new@example.com", "New User"));

    verify(identityProvider).provisionVerifiedAccount("linkedin:sub5", "new@example.com", "New User");
    assertThat(result.firebaseUid()).isEqualTo("linkedin:sub5");
    assertThat(result.linkedinId()).isEqualTo("sub5");
    assertThat(result.emailVerified()).isTrue();
    assertThat(result.role()).isEqualTo(UserRole.USER);
    verify(profileRepo).save(any());
    ArgumentCaptor<ProfilePrivateInfo> info = ArgumentCaptor.forClass(ProfilePrivateInfo.class);
    verify(privateInfoRepo).save(info.capture());
    assertThat(info.getValue().fullName()).isEqualTo("New User");
  }

  @Test
  void writes_no_app_row_when_firebase_finds_the_email_taken_meanwhile() {
    when(userRepo.findByLinkedinId("sub6")).thenReturn(Optional.empty());
    when(userRepo.findByEmail("race@example.com")).thenReturn(Optional.empty());
    doThrow(new AccountExistsException())
        .when(identityProvider)
        .provisionVerifiedAccount("linkedin:sub6", "race@example.com", "R");

    assertThatThrownBy(() -> service.resolve(verified("sub6", "race@example.com", "R")))
        .isInstanceOf(AccountExistsException.class);

    verify(userRepo, never()).save(any());
  }

  @Test
  void uses_the_row_a_concurrent_sign_in_of_the_same_member_inserted() {
    User winner = user("twice@example.com", "sub7", "linkedin:sub7");
    when(userRepo.findByLinkedinId("sub7")).thenReturn(Optional.empty(), Optional.of(winner));
    when(userRepo.findByEmail("twice@example.com")).thenReturn(Optional.empty());
    when(userRepo.save(any())).thenThrow(new DataIntegrityViolationException("users_email_key"));

    assertThat(service.resolve(verified("sub7", "twice@example.com", "T"))).isSameAs(winner);
  }

  @Test
  void returns_a_linked_member_untouched_when_the_email_is_unverified() {
    User existing = user("sub8@example.com", "sub8", null);
    when(userRepo.findByLinkedinId("sub8")).thenReturn(Optional.of(existing));

    User result = service.resolve(new LinkedInIdentity("sub8", "sub8@example.com", false, "E"));

    assertThat(result).isSameAs(existing);
    verifyNoInteractions(identityProvider);
    verify(userRepo, never()).save(any());
  }

  @Test
  void refuses_an_unverified_email_without_a_link() {
    when(userRepo.findByLinkedinId("sub9")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.resolve(new LinkedInIdentity("sub9", "fresh@example.com", false, "F")))
        .isInstanceOf(SecurityException.class);

    verify(userRepo, never()).findByEmail(any());
    verify(userRepo, never()).save(any());
    verifyNoInteractions(profileRepo, privateInfoRepo, identityProvider);
  }

  private LinkedInIdentity verified(String sub, String email, String fullName) {
    return new LinkedInIdentity(sub, email, true, fullName);
  }

  private User user(String email, String linkedinId, String firebaseUid) {
    return new User(
        userId,
        email,
        null,
        linkedinId,
        firebaseUid,
        UserRole.USER,
        true,
        false,
        Instant.now(),
        Instant.now());
  }

  private User withId(User u) {
    return new User(
        u.id() != null ? u.id() : userId,
        u.email(),
        u.googleId(),
        u.linkedinId(),
        u.firebaseUid(),
        u.role(),
        u.emailVerified(),
        u.onboardingComplete(),
        u.createdAt(),
        u.updatedAt());
  }
}
