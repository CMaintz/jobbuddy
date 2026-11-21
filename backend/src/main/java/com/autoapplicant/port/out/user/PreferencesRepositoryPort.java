package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.UserPreferences;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreferencesRepositoryPort {
    UserPreferences save(UserPreferences preferences);
    Optional<UserPreferences> findByUserId(UUID userId);
    List<UserPreferences> findAllWithNotificationsEnabled();
    /** Every user's saved preferences; for decisions shared across users, like enrichment scope. */
    List<UserPreferences> findAll();
}
