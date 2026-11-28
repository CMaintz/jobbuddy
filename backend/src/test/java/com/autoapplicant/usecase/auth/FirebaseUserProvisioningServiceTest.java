package com.autoapplicant.usecase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirebaseUserProvisioningServiceTest {

    private static final Instant CREATED = Instant.parse("2026-01-05T09:00:00Z");

    @Mock UserRepositoryPort               userRepo;
    @Mock ProfileRepositoryPort            profileRepo;
    @Mock ProfilePrivateInfoRepositoryPort privateInfoRepo;
    @Mock FirebaseAuth                     firebaseAuth;

    FirebaseUserProvisioningService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FirebaseUserProvisioningService(userRepo, profileRepo, privateInfoRepo, firebaseAuth);
    }

    @Test
    void an_unknown_sign_in_becomes_a_plain_user_with_a_default_profile_and_their_name()
            throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid("uid1")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        User result = service.findOrCreate("uid1", "new@example.com", "New User");

        assertThat(result.role()).isEqualTo(UserRole.USER);
        assertThat(result.firebaseUid()).isEqualTo("uid1");
        assertThat(result.onboardingComplete()).isFalse();
        ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepo).save(profile.capture());
        assertThat(profile.getValue().userId()).isEqualTo(userId);
        ArgumentCaptor<ProfilePrivateInfo> privateInfo = ArgumentCaptor.forClass(ProfilePrivateInfo.class);
        verify(privateInfoRepo).save(privateInfo.capture());
        assertThat(privateInfo.getValue().fullName()).isEqualTo("New User");
        assertThat(privateInfo.getValue().userId()).isEqualTo(userId);
        verify(firebaseAuth).setCustomUserClaims("uid1", Map.of("role", "USER"));
    }

    @Test
    void a_blank_display_name_writes_no_private_info() {
        when(userRepo.findByFirebaseUid("uid1")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        service.findOrCreate("uid1", "new@example.com", "  ");

        verify(privateInfoRepo, never()).save(any());
    }

    @Test
    void returns_existing_user_when_firebase_uid_already_known() {
        User existing = user(UserRole.USER, "uid1");
        when(userRepo.findByFirebaseUid("uid1")).thenReturn(Optional.of(existing));

        User result = service.findOrCreate("uid1", "alice@example.com", "Alice");

        assertThat(result).isSameAs(existing);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void linking_an_existing_email_account_keeps_its_role_and_syncs_the_claim()
            throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid("uid2")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(user(UserRole.ADMIN, null)));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.findOrCreate("uid2", "alice@example.com", "Alice");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(cap.capture());
        assertThat(cap.getValue().id()).isEqualTo(userId);
        assertThat(cap.getValue().firebaseUid()).isEqualTo("uid2");
        assertThat(cap.getValue().role()).isEqualTo(UserRole.ADMIN);
        assertThat(cap.getValue().onboardingComplete()).isTrue();
        verify(firebaseAuth).setCustomUserClaims("uid2", Map.of("role", "ADMIN"));
        verify(profileRepo, never()).save(any());
    }

    @Test
    void promoting_a_user_saves_the_role_and_pushes_it_to_their_token_claims()
            throws FirebaseAuthException {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user(UserRole.USER, "uid1")));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.setRole(userId, UserRole.ADMIN);

        assertThat(result.role()).isEqualTo(UserRole.ADMIN);
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.onboardingComplete()).isTrue();
        verify(firebaseAuth).setCustomUserClaims("uid1", Map.of("role", "ADMIN"));
    }

    @Test
    void a_role_change_for_an_account_never_signed_in_with_firebase_skips_the_claim() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user(UserRole.ADMIN, null)));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.setRole(userId, UserRole.USER);

        assertThat(result.role()).isEqualTo(UserRole.USER);
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void setting_the_role_of_an_unknown_user_fails_without_saving() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setRole(userId, UserRole.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(firebaseAuth);
    }

    private User withId(User unsaved) {
        return new User(userId, unsaved.email(), unsaved.googleId(), unsaved.linkedinId(),
                unsaved.firebaseUid(), unsaved.role(), unsaved.emailVerified(),
                unsaved.onboardingComplete(), CREATED, CREATED);
    }

    private User user(UserRole role, String firebaseUid) {
        return new User(userId, "alice@example.com", null, null, firebaseUid,
                role, true, true, CREATED, CREATED);
    }
}
