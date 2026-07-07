package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.in.document.*;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PromptTemplateService implements
        CreatePromptTemplateUseCase, GetPromptTemplatesUseCase, DuplicatePromptTemplateUseCase,
        UpdatePromptTemplateUseCase, DeletePromptTemplateUseCase {

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
    public PromptTemplate updateTemplate(UUID templateId, UUID userId, boolean isAdmin, PromptTemplate changes) {
        PromptTemplate existing = requireModifiable(templateId, userId, isAdmin);
        PromptTemplate toSave = new PromptTemplate(existing.id(), existing.userId(),
                changes.name(), changes.category(), changes.description(),
                changes.systemPrompt(), changes.userPrompt(), changes.outputConstraints(),
                changes.isPublic(), existing.parentTemplateId(),
                existing.versionNumber() + 1, existing.createdAt(), null, existing.isSystem());
        return repo.save(toSave);
    }

    @Override
    public void deleteTemplate(UUID templateId, UUID userId, boolean isAdmin) {
        requireModifiable(templateId, userId, isAdmin);
        repo.deleteById(templateId);
    }

    /** System templates are admin-only; user templates belong to their creator. */
    private PromptTemplate requireModifiable(UUID templateId, UUID userId, boolean isAdmin) {
        PromptTemplate existing = repo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        if (existing.isSystem()) {
            if (!isAdmin) throw new SecurityException("System templates can only be changed by an admin");
        } else if (!userId.equals(existing.userId())) {
            throw new SecurityException("You can only change your own templates");
        }
        return existing;
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
