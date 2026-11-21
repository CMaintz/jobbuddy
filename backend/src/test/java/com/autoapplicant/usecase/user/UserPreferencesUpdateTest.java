package com.autoapplicant.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                false, "DAILY", null, null, null, List.of("SALES"));

        assertThat(service.updatePreferences(user, incoming).excludedCategories()).containsExactly("SALES");
    }
}
