package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.SpokenLanguage;

import java.util.List;
import java.util.UUID;

public interface ManageSpokenLanguagesUseCase {
    List<SpokenLanguage> getLanguages(UUID userId);
    SpokenLanguage save(SpokenLanguage language);
    void delete(UUID id, UUID userId);
}
