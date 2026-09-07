package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.UserPreferences;

import java.util.Optional;
import java.util.UUID;

public interface PreferencesRepositoryPort {
    UserPreferences save(UserPreferences preferences);
    Optional<UserPreferences> findByUserId(UUID userId);
}
