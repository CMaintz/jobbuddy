package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.CvSectionPrompts;
import com.autoapplicant.port.in.document.ManageCvSectionPromptsUseCase;
import com.autoapplicant.port.out.document.CvSectionPromptsRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class CvSectionPromptsService implements ManageCvSectionPromptsUseCase {

    private final CvSectionPromptsRepositoryPort repo;

    public CvSectionPromptsService(CvSectionPromptsRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public CvSectionPrompts get(UUID userId) {
        return repo.findByUserId(userId).orElseGet(() -> CvSectionPrompts.empty(userId));
    }

    @Override
    public CvSectionPrompts save(UUID userId, Map<CvSection, String> prompts) {
        return repo.save(userId, prompts);
    }
}
