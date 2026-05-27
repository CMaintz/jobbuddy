package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.SpokenLanguage;

import java.util.List;
import java.util.UUID;

public interface SpokenLanguageRepositoryPort {
    SpokenLanguage save(SpokenLanguage language);
    List<SpokenLanguage> findByUserId(UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
