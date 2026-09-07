package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.SpokenLanguage;
import com.autoapplicant.port.in.user.ManageSpokenLanguagesUseCase;
import com.autoapplicant.port.out.user.SpokenLanguageRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SpokenLanguageService implements ManageSpokenLanguagesUseCase {

    private final SpokenLanguageRepositoryPort languageRepo;

    public SpokenLanguageService(SpokenLanguageRepositoryPort languageRepo) {
        this.languageRepo = languageRepo;
    }

    @Override
    public List<SpokenLanguage> getLanguages(UUID userId) {
        return languageRepo.findByUserId(userId);
    }

    @Override
    public SpokenLanguage save(SpokenLanguage language) {
        return languageRepo.save(language);
    }

    @Override
    public void delete(UUID id, UUID userId) {
        languageRepo.deleteByIdAndUserId(id, userId);
    }
}
