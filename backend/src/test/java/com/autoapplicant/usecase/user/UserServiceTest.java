package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.user.*;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepositoryPort              userRepo;
    @Mock ProfileRepositoryPort           profileRepo;
    @Mock PreferencesRepositoryPort       prefsRepo;
    @Mock ProfileEmbeddingRepositoryPort  profileEmbeddingRepo;
    @Mock WorkExperienceRepositoryPort    workExpRepo;
    @Mock ProjectRepositoryPort           projectRepo;
    @Mock CertificationRepositoryPort     certRepo;
    @Mock AiProviderPort                  aiProvider;

    UserService service;
    UUID        userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepo, profileRepo, prefsRepo, profileEmbeddingRepo,
                workExpRepo, projectRepo, certRepo, aiProvider);
    }

    // ── getUser ───────────────────────────────────────────────────────────────

    @Test
    void get_user_delegates_to_user_repo() {
        User user = user(userId, "alice@example.com", "uid1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        assertThat(service.getUser(userId)).contains(user);
    }

    @Test
    void get_user_returns_empty_when_not_found() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        assertThat(service.getUser(userId)).isEmpty();
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void get_profile_delegates_to_profile_repo() {
        Profile profile = new Profile(UUID.randomUUID(), userId, null, null, null,
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
        Profile incoming = new Profile(null, null, null, null, 5,
                List.of("Java"), List.of(), List.of(),
                60000, 90000, "DKK", RemoteType.HYBRID, null, null, null);
        Profile savedProfile = new Profile(UUID.randomUUID(), userId, null, null, 5,
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
                UserRole.USER, true, false, Instant.now(), Instant.now());
    }
}
