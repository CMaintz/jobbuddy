package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.UserPreferences;

import java.util.UUID;

public interface UpdatePreferencesUseCase {
    UserPreferences updatePreferences(UUID userId, UserPreferences preferences);
}
