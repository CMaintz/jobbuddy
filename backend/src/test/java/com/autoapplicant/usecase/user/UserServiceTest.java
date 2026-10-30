package com.autoapplicant.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.user.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant CREATED = Instant.parse("2026-01-05T09:00:00Z");

    @Mock UserRepositoryPort              userRepo;
    @Mock ProfileRepositoryPort           profileRepo;
    @Mock PreferencesRepositoryPort       prefsRepo;
    @Mock ApplicationEventPublisher       events;

    UserService service;
    UUID        userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepo, profileRepo, prefsRepo, events);
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void update_profile_saves_with_correct_user_id() {
        Profile incoming = new Profile(null, null, null, null, 5,
                List.of(), List.of(),
                60000, 90000, "DKK", RemoteType.HYBRID, null, null, null);
        Profile savedProfile = new Profile(UUID.randomUUID(), userId, null, null, 5,
                List.of(), List.of(),
                60000, 90000, "DKK", RemoteType.HYBRID, null, null, null);
        when(profileRepo.save(any())).thenReturn(savedProfile);

        Profile result = service.updateProfile(userId, incoming);

        ArgumentCaptor<Profile> cap = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepo).save(cap.capture());
        assertThat(cap.getValue().userId()).isEqualTo(userId);
        assertThat(result).isSameAs(savedProfile);
    }

    @Test
    void a_partial_profile_update_keeps_what_it_leaves_out_and_clears_what_it_blanks() {
        Profile existing = new Profile(UUID.randomUUID(), userId, "Backend engineer", "Ten years of Java",
                5, List.of("Danish"), List.of("Climbing"), 600000, 750000, "DKK",
                RemoteType.HYBRID, EmploymentType.FULL_TIME, CREATED, CREATED);
        when(profileRepo.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Profile partial = new Profile(null, null, null, "", 7, null, List.of(),
                null, 800000, null, RemoteType.REMOTE, null, null, null);

        Profile saved = service.updateProfile(userId, partial);

        assertThat(saved.headline()).isEqualTo("Backend engineer");
        assertThat(saved.summary()).isEmpty();
        assertThat(saved.yearsExperience()).isEqualTo(7);
        assertThat(saved.languages()).containsExactly("Danish");
        assertThat(saved.interests()).isEmpty();
        assertThat(saved.desiredSalaryMin()).isEqualTo(600000);
        assertThat(saved.desiredSalaryMax()).isEqualTo(800000);
        assertThat(saved.desiredCurrency()).isEqualTo("DKK");
        assertThat(saved.remotePreference()).isEqualTo(RemoteType.REMOTE);
        assertThat(saved.employmentTypePreference()).isEqualTo(EmploymentType.FULL_TIME);
    }

    @Test
    void update_profile_publishes_saved_event_instead_of_embedding_inline() {
        Profile savedProfile = new Profile(UUID.randomUUID(), userId, "Engineer", null, null,
                List.of(), List.of(),
                null, null, "DKK", null, null, null, null);
        when(profileRepo.save(any())).thenReturn(savedProfile);

        Profile result = service.updateProfile(userId, savedProfile);

        ArgumentCaptor<ProfileSavedEvent> cap = ArgumentCaptor.forClass(ProfileSavedEvent.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue()).isEqualTo(new ProfileSavedEvent(userId));
        assertThat(result).isSameAs(savedProfile);
    }

    // ── updatePreferences ─────────────────────────────────────────────────────

    @Test
    void preferences_are_saved_for_the_caller_whatever_user_the_payload_names() {
        UserPreferences payload = new UserPreferences(UUID.randomUUID(), UUID.randomUUID(),
                List.of("Copenhagen"), List.of("Frederiksberg"), List.of("Kotlin"), List.of("On-call"),
                List.of("Initech"), List.of("REMOTE"), List.of("FULL_TIME"), List.of("SENIOR"),
                List.of("Fintech"), 600000, 800000, 25, true, "DAILY", 4, CREATED, CREATED);
        when(prefsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserPreferences saved = service.updatePreferences(userId, payload);

        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.id()).isNull();
        assertThat(saved).usingRecursiveComparison()
                .ignoringFields("id", "userId", "createdAt", "updatedAt")
                .isEqualTo(payload);
    }

    // ── completeOnboarding ────────────────────────────────────────────────────

    @Test
    void completing_onboarding_flips_only_that_flag() {
        User user = new User(userId, "alice@example.com", "g1", "li1", "uid1",
                UserRole.ADMIN, true, false, CREATED, CREATED);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        service.completeOnboarding(userId);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(cap.capture());
        assertThat(cap.getValue().onboardingComplete()).isTrue();
        assertThat(cap.getValue()).usingRecursiveComparison()
                .ignoringFields("onboardingComplete")
                .isEqualTo(user);
    }

    @Test
    void completing_onboarding_for_an_unknown_user_fails_without_saving() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeOnboarding(userId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).save(any());
    }
}
