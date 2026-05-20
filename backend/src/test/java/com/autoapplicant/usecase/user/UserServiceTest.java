package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.user.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepositoryPort        userRepo;
    @Mock ProfileRepositoryPort     profileRepo;
    @Mock PreferencesRepositoryPort prefsRepo;
    @Mock FirebaseAuth              firebaseAuth;

    UserService service;
    UUID        userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepo, profileRepo, prefsRepo, firebaseAuth);
    }

    // ── findOrCreateUserFromFirebase — new user ───────────────────────────────

    @Test
    void creates_new_user_when_firebase_uid_and_email_are_unknown() throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid("uid1")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        User saved = user(userId, "new@example.com", "uid1");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        User result = service.findOrCreateUserFromFirebase("uid1", "new@example.com", "New User");

        assertThat(result).isSameAs(saved);
        verify(userRepo).save(any());
        verify(profileRepo).save(any());
        verify(firebaseAuth).setCustomUserClaims(eq("uid1"), any());
    }

    @Test
    void returns_existing_user_when_firebase_uid_already_known() throws FirebaseAuthException {
        User existing = user(userId, "alice@example.com", "uid1");
        when(userRepo.findByFirebaseUid("uid1")).thenReturn(Optional.of(existing));

        User result = service.findOrCreateUserFromFirebase("uid1", "alice@example.com", "Alice");

        assertThat(result).isSameAs(existing);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void links_firebase_uid_to_existing_email_account() throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid("uid2")).thenReturn(Optional.empty());
        User byEmail = user(userId, "linked@example.com", null);
        when(userRepo.findByEmail("linked@example.com")).thenReturn(Optional.of(byEmail));
        User linked = user(userId, "linked@example.com", "uid2");
        when(userRepo.save(any())).thenReturn(linked);

        service.findOrCreateUserFromFirebase("uid2", "linked@example.com", "Linked");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(cap.capture());
        assertThat(cap.getValue().firebaseUid()).isEqualTo("uid2");
    }

    @Test
    void new_user_gets_default_profile_created() throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid(any())).thenReturn(Optional.empty());
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        User saved = user(userId, "p@example.com", "uid3");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        service.findOrCreateUserFromFirebase("uid3", "p@example.com", "Profile User");

        ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepo).save(cap.capture());
        assertThat(cap.getValue().fullName()).isEqualTo("Profile User");
        assertThat(cap.getValue().userId()).isEqualTo(userId);
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void get_profile_delegates_to_profile_repo() {
        Profile profile = new Profile(UUID.randomUUID(), userId, "Alice", null, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                null, null, "DKK", null, null, null, null);
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(profile));

        assertThat(service.getProfile(userId)).contains(profile);
    }

    @Test
    void get_profile_returns_empty_when_not_found() {
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.empty());
        assertThat(service.getProfile(userId)).isEmpty();
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void update_profile_saves_with_correct_user_id() {
        Profile incoming = new Profile(null, null, "Updated Name", "Engineer", "Summary",
                "Copenhagen", null, null, null, null, null, null, 5,
                List.of("Java"), List.of(), List.of(),
                60000, 90000, "DKK", RemoteType.HYBRID, null, null, null);
        Profile savedProfile = new Profile(UUID.randomUUID(), userId, "Updated Name", "Engineer",
                "Summary", "Copenhagen", null, null, null, null, null, null, 5,
                List.of("Java"), List.of(), List.of(),
                60000, 90000, "DKK", RemoteType.HYBRID, null, null, null);
        when(profileRepo.save(any())).thenReturn(savedProfile);

        Profile result = service.updateProfile(userId, incoming);

        ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepo).save(cap.capture());
        assertThat(cap.getValue().userId()).isEqualTo(userId);
        assertThat(result).isSameAs(savedProfile);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User user(UUID id, String email, String firebaseUid) {
        return new User(id, email, null, null, firebaseUid,
                UserRole.USER, true, Instant.now(), Instant.now());
    }
}
