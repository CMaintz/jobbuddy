package com.autoapplicant.usecase.user;

import com.autoapplicant.adapter.security.JwtTokenProvider;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepositoryPort        userRepo;
    @Mock ProfileRepositoryPort     profileRepo;
    @Mock PreferencesRepositoryPort prefsRepo;
    @Mock JwtTokenProvider          jwtProvider;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    UserService     service;

    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepo, profileRepo, prefsRepo, passwordEncoder, jwtProvider);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_saves_user_with_hashed_password() {
        when(userRepo.existsByEmail("alice@example.com")).thenReturn(false);
        User saved = user(userId, "alice@example.com", "$bcrypt$hash");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        service.register("alice@example.com", "password123", "Alice");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(cap.capture());
        User captured = cap.getValue();
        // Password must be hashed, not plain text
        assertThat(captured.passwordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", captured.passwordHash())).isTrue();
    }

    @Test
    void register_sets_role_to_user() {
        when(userRepo.existsByEmail(any())).thenReturn(false);
        User saved = user(userId, "alice@example.com", "hash");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        service.register("alice@example.com", "password123", "Alice");

        verify(userRepo).save(argThat(u -> u.role() == UserRole.USER));
    }

    @Test
    void register_creates_initial_profile_with_full_name() {
        when(userRepo.existsByEmail(any())).thenReturn(false);
        User saved = user(userId, "alice@example.com", "hash");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        service.register("alice@example.com", "password123", "Alice Smith");

        ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepo).save(cap.capture());
        assertThat(cap.getValue().fullName()).isEqualTo("Alice Smith");
        assertThat(cap.getValue().userId()).isEqualTo(userId);
    }

    @Test
    void register_throws_when_email_already_exists() {
        when(userRepo.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("dup@example.com", "password123", "Dup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepo, never()).save(any());
    }

    @Test
    void register_returns_saved_user() {
        when(userRepo.existsByEmail(any())).thenReturn(false);
        User saved = user(userId, "alice@example.com", "hash");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        User result = service.register("alice@example.com", "password123", "Alice");
        assertThat(result).isSameAs(saved);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_returns_jwt_for_valid_credentials() {
        String hash = passwordEncoder.encode("secret");
        User found = user(userId, "bob@example.com", hash);
        when(userRepo.findByEmail("bob@example.com")).thenReturn(Optional.of(found));
        when(jwtProvider.generateToken(userId, "bob@example.com", UserRole.USER))
                .thenReturn("jwt-token");

        String token = service.login("bob@example.com", "secret");
        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    void login_throws_for_unknown_email() {
        when(userRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("unknown@example.com", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_throws_for_wrong_password() {
        String hash = passwordEncoder.encode("correct");
        User found = user(userId, "carol@example.com", hash);
        when(userRepo.findByEmail("carol@example.com")).thenReturn(Optional.of(found));

        assertThatThrownBy(() -> service.login("carol@example.com", "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");

        verify(jwtProvider, never()).generateToken(any(), any(), any());
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void get_profile_delegates_to_profile_repo() {
        Profile profile = new Profile(UUID.randomUUID(), userId, "Alice", null, null,
                null, null, null, null, null, null,
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
                "Copenhagen", null, null, null, null, 5,
                List.of("Java"), List.of(), List.of(),
                60000, 90000, "DKK", RemoteType.HYBRID, null, null, null);
        Profile savedProfile = new Profile(UUID.randomUUID(), userId, "Updated Name", "Engineer",
                "Summary", "Copenhagen", null, null, null, null, 5,
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

    private User user(UUID id, String email, String hash) {
        return new User(id, email, hash, null, UserRole.USER, false, Instant.now(), Instant.now());
    }
}
