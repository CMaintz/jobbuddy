package com.autoapplicant.usecase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.common.AccountConflictException;
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
  void returns_a_linked_member_whose_firebase_account_is_already_verified() {
    User existing = user("sub1@example.com", "sub1", "fb-1");
    when(userRepo.findByLinkedinId("sub1")).thenReturn(Optional.of(existing));
    when(identityProvider.ensureVerifiedAccount("fb-1", "sub1@example.com", "Sub One"))
        .thenReturn("fb-1");

    User result = service.resolve(verified("sub1", "sub1@example.com", "Sub One"));

    assertThat(result).isSameAs(existing);
    verify(userRepo, never()).save(any());
    verifyNoInteractions(profileRepo, privateInfoRepo);
  }

  @Test
  void links_an_existing_email_account_and_signs_into_its_firebase_account() {
    when(userRepo.findByLinkedinId("sub2")).thenReturn(Optional.empty());
    User google = user("linked@example.com", null, "google-uid");
    when(userRepo.findByEmail("linked@example.com")).thenReturn(Optional.of(google));
    when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(identityProvider.ensureVerifiedAccount("google-uid", "linked@example.com", "Linked"))
        .thenReturn("google-uid");

    User result = service.resolve(verified("sub2", "linked@example.com", "Linked"));

    assertThat(result.linkedinId()).isEqualTo("sub2");
    assertThat(result.id()).isEqualTo(userId);
    assertThat(result.firebaseUid()).isEqualTo("google-uid");
    verifyNoInteractions(profileRepo, privateInfoRepo);
  }

  @Test
  void a_new_member_gets_a_verified_firebase_account_the_backend_will_accept() {
    when(userRepo.findByLinkedinId("sub3")).thenReturn(Optional.empty());
    when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(userRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(identityProvider.ensureVerifiedAccount("linkedin:sub3", "new@example.com", "New User"))
        .thenReturn("linkedin:sub3");

    User result = service.resolve(verified("sub3", "new@example.com", "New User"));

    assertThat(result.firebaseUid()).isEqualTo("linkedin:sub3");
    assertThat(result.emailVerified()).isTrue();
    assertThat(result.role()).isEqualTo(UserRole.USER);
    verify(profileRepo).save(any());
    ArgumentCaptor<ProfilePrivateInfo> info = ArgumentCaptor.forClass(ProfilePrivateInfo.class);
    verify(privateInfoRepo).save(info.capture());
    assertThat(info.getValue().fullName()).isEqualTo("New User");
  }

  @Test
  void a_new_member_whose_email_has_a_verified_firebase_account_signs_into_that_account() {
    when(userRepo.findByLinkedinId("sub4")).thenReturn(Optional.empty());
    when(userRepo.findByEmail("g@example.com")).thenReturn(Optional.empty());
    when(userRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(identityProvider.ensureVerifiedAccount("linkedin:sub4", "g@example.com", "   "))
        .thenReturn("existing-google-uid");

    User result = service.resolve(verified("sub4", "g@example.com", "   "));

    assertThat(result.firebaseUid()).isEqualTo("existing-google-uid");
    verify(privateInfoRepo, never()).save(any());
  }

  @Test
  void returns_a_linked_member_untouched_when_the_email_is_unverified() {
    User existing = user("sub5@example.com", "sub5", null);
    when(userRepo.findByLinkedinId("sub5")).thenReturn(Optional.of(existing));

    User result = service.resolve(new LinkedInIdentity("sub5", "sub5@example.com", false, "Five"));

    assertThat(result).isSameAs(existing);
    verifyNoInteractions(identityProvider);
    verify(userRepo, never()).save(any());
  }

  @Test
  void refuses_to_link_an_existing_email_account_on_an_unverified_email() {
    when(userRepo.findByLinkedinId("sub6")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.resolve(
                    new LinkedInIdentity("sub6", "victim@example.com", false, "Attacker")))
        .isInstanceOf(SecurityException.class);

    verify(userRepo, never()).findByEmail(any());
    verify(userRepo, never()).save(any());
    verifyNoInteractions(profileRepo, privateInfoRepo, identityProvider);
  }

  @Test
  void refuses_to_create_an_account_on_an_unverified_email() {
    when(userRepo.findByLinkedinId("sub7")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.resolve(new LinkedInIdentity("sub7", "fresh@example.com", false, "F")))
        .isInstanceOf(SecurityException.class);

    verify(userRepo, never()).save(any());
    verifyNoInteractions(profileRepo, privateInfoRepo, identityProvider);
  }

  @Test
  void uses_the_row_a_concurrent_first_sign_in_inserted() {
    User winner = user("race@example.com", "sub8", "linkedin:sub8");
    when(userRepo.findByLinkedinId("sub8")).thenReturn(Optional.empty(), Optional.of(winner));
    when(userRepo.findByEmail("race@example.com")).thenReturn(Optional.empty());
    when(userRepo.save(any())).thenThrow(new DataIntegrityViolationException("users_email_key"));
    when(identityProvider.ensureVerifiedAccount(anyString(), anyString(), anyString()))
        .thenReturn("linkedin:sub8");

    User result = service.resolve(verified("sub8", "race@example.com", "Race"));

    assertThat(result).isSameAs(winner);
  }

  @Test
  void surfaces_an_unverified_firebase_account_holding_the_email_as_a_conflict() {
    when(userRepo.findByLinkedinId("sub9")).thenReturn(Optional.empty());
    when(userRepo.findByEmail("taken@example.com")).thenReturn(Optional.empty());
    when(userRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(identityProvider.ensureVerifiedAccount(anyString(), anyString(), anyString()))
        .thenThrow(new AccountConflictException("unverified"));

    assertThatThrownBy(() -> service.resolve(verified("sub9", "taken@example.com", "T")))
        .isInstanceOf(AccountConflictException.class);
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
