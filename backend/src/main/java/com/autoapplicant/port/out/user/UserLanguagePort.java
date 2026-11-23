package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.UiLanguage;
import java.util.Optional;
import java.util.UUID;

/**
 * The user's UI language, kept apart from {@link UserRepositoryPort#save}: saving an account must
 * never reset a language the user picked.
 */
public interface UserLanguagePort {
    Optional<UiLanguage> findLanguage(UUID userId);

    void saveLanguage(UUID userId, UiLanguage language);
}
