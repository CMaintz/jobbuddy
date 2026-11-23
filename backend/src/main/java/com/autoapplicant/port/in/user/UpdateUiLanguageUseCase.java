package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.UiLanguage;
import java.util.UUID;

/** Records the language the user reads the app in, so emails sent later match it. */
public interface UpdateUiLanguageUseCase {
    void updateLanguage(UUID userId, UiLanguage language);
}
