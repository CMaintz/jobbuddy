package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.in.document.*;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PromptTemplateService implements
        CreatePromptTemplateUseCase, GetPromptTemplatesUseCase, DuplicatePromptTemplateUseCase {

    private final PromptTemplateRepositoryPort repo;

    public PromptTemplateService(PromptTemplateRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public PromptTemplate createTemplate(UUID userId, PromptTemplate template) {
        PromptTemplate toSave = new PromptTemplate(null, userId, template.name(),
                template.category(), template.description(), template.systemPrompt(),
                template.userPrompt(), template.outputConstraints(), template.isPublic(),
                template.parentTemplateId(), 1, null, null, false);
        return repo.save(toSave);
    }

    @Override
    public List<PromptTemplate> getTemplates(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public PromptTemplate duplicate(UUID templateId, UUID userId, String newName) {
        PromptTemplate original = repo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        PromptTemplate copy = new PromptTemplate(null, userId,
                newName != null ? newName : original.name() + " (copy)",
                original.category(), original.description(), original.systemPrompt(),
                original.userPrompt(), original.outputConstraints(), false,
                original.id(), original.versionNumber() + 1, null, null, false);
        return repo.save(copy);
    }
}
