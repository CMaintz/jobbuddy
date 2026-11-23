package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.UiLanguage;
import com.autoapplicant.port.in.user.UpdateUiLanguageUseCase;
import com.autoapplicant.port.out.user.UserLanguagePort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UiLanguageService implements UpdateUiLanguageUseCase {

    private final UserLanguagePort languages;

    public UiLanguageService(UserLanguagePort languages) {
        this.languages = languages;
    }

    @Override
    public void updateLanguage(UUID userId, UiLanguage language) {
        languages.saveLanguage(userId, language);
    }
}
