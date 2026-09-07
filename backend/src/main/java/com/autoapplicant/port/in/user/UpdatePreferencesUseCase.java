package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.UserPreferences;

import java.util.Optional;
import java.util.UUID;

public interface UpdatePreferencesUseCase {
    Optional<UserPreferences> getPreferences(UUID userId);
    UserPreferences updatePreferences(UUID userId, UserPreferences preferences);
}
