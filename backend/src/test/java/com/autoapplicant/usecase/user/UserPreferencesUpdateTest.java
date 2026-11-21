package com.autoapplicant.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.EnrichmentArea;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class UserPreferencesUpdateTest {

    @Test
    void savingPreferencesKeepsTheSkippedCategories() {
        // Bug: the update rebuilds the record field by field and leaves the new one out, so the
        // skip list is wiped on every save from Settings.
        PreferencesRepositoryPort prefsRepo = mock(PreferencesRepositoryPort.class);
        when(prefsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UserService service = new UserService(mock(UserRepositoryPort.class),
                mock(ProfileRepositoryPort.class), prefsRepo, mock(ApplicationEventPublisher.class));
        UUID user = UUID.randomUUID();
        UserPreferences incoming = new UserPreferences(null, user, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, "DAILY", null, null, null, List.of("SALES"), null);

        assertThat(service.updatePreferences(user, incoming).excludedCategories()).containsExactly("SALES");
    }

    private static UserPreferences withArea(EnrichmentArea area) {
        return new UserPreferences(null, UUID.randomUUID(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, "DAILY", null, null, null, List.of(), area);
    }

    private static UserService serviceSavingAsIs() {
        PreferencesRepositoryPort prefsRepo = mock(PreferencesRepositoryPort.class);
        when(prefsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new UserService(mock(UserRepositoryPort.class), mock(ProfileRepositoryPort.class),
                prefsRepo, mock(ApplicationEventPublisher.class));
    }

    @Test
    void savingPreferencesKeepsTheCommuteArea() {
        // Bug: the update rebuild leaves the area out, so it resets to the default on save.
        EnrichmentArea area = new EnrichmentArea("Viby J", 70);

        assertThat(serviceSavingAsIs().updatePreferences(UUID.randomUUID(), withArea(area))
                .enrichmentArea()).isEqualTo(area);
    }

    @Test
    void aHomeThatCannotBeFoundIsRejected() {
        // Bug: an unknown home is saved, the queue cannot place it and silently falls back to
        // the default, and she never learns her setting does nothing.
        UserPreferences unknownHome = withArea(new EnrichmentArea("Atlantis", 50));

        assertThatThrownBy(() -> serviceSavingAsIs().updatePreferences(UUID.randomUUID(), unknownHome))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Atlantis");
    }

    @Test
    void aRadiusOutsideTheSensibleRangeIsRejected() {
        // Bug: a radius of 0 or a negative one enriches nothing but remote postings.
        UserPreferences noRadius = withArea(new EnrichmentArea("Viby J", 0));

        assertThatThrownBy(() -> serviceSavingAsIs().updatePreferences(UUID.randomUUID(), noRadius))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPayloadWithoutTheNewFieldsKeepsWhatIsSaved() {
        // Bug: onboarding saves preferences with its own short payload, and a missing field is
        // stored as empty, wiping the skip list and resetting the area she set in Settings.
        PreferencesRepositoryPort prefsRepo = mock(PreferencesRepositoryPort.class);
        when(prefsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID user = UUID.randomUUID();
        EnrichmentArea area = new EnrichmentArea("Viby J", 70);
        UserPreferences saved = new UserPreferences(null, user, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                false, "DAILY", null, null, null, List.of("SALES"), area);
        when(prefsRepo.findByUserId(user)).thenReturn(java.util.Optional.of(saved));
        UserService service = new UserService(mock(UserRepositoryPort.class),
                mock(ProfileRepositoryPort.class), prefsRepo, mock(ApplicationEventPublisher.class));

        UserPreferences result = service.updatePreferences(user, new UserPreferences(null, user, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, false, "DAILY", 5, null, null, null, null));

        assertThat(result.excludedCategories()).containsExactly("SALES");
        assertThat(result.enrichmentArea()).isEqualTo(area);
    }
}
