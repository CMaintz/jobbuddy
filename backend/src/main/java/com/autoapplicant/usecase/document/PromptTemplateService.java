package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptCategory;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.in.document.*;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService implements
        CreatePromptTemplateUseCase, GetPromptTemplatesUseCase, DuplicatePromptTemplateUseCase,
        UpdatePromptTemplateUseCase, DeletePromptTemplateUseCase, FavouritePromptTemplateUseCase,
        SelectDefaultPromptUseCase, BrowsePublicPromptsUseCase {

    /** Enough for real variety per kind of prompt, few enough that a picker stays scannable. */
    static final int MAX_OWN_TEMPLATES_PER_CATEGORY = 10;

    private final PromptTemplateRepositoryPort repo;

    public PromptTemplateService(PromptTemplateRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public void favourite(UUID userId, UUID templateId) {
        repo.findById(templateId)
                .filter(t -> t.isVisibleTo(userId))
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        repo.addFavourite(userId, templateId);
    }

    @Override
    public void unfavourite(UUID userId, UUID templateId) {
        repo.removeFavourite(userId, templateId);
    }

    @Override
    public java.util.Set<UUID> getFavouriteIds(UUID userId) {
        return repo.findFavouriteTemplateIds(userId);
    }

    @Override
    public PromptTemplate createTemplate(UUID userId, PromptTemplate template) {
        requireRoomFor(userId, template.category());
        PromptTemplate toSave = new PromptTemplate(null, userId, template.name(),
                template.category(), template.description(),
                personaFor(template.category(), template.systemPrompt()),
                template.userPrompt(), template.outputConstraints(), template.isPublic(),
                template.parentTemplateId(), 1, null, null, false,
                template.tags() != null ? template.tags() : List.of(), 0, false, false, null);
        return repo.save(toSave);
    }

    @Override
    public List<PromptTemplate> getTemplates(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public List<PromptTemplate> browsePublic(UUID callerId) {
        // Your own shared prompts are already in your library; seeing them again in the browser
        // would just be a second copy of a row you can already edit.
        return repo.findPublic().stream()
                .filter(t -> t.userId() != null && !t.userId().equals(callerId))
                .toList();
    }

    @Override
    public PromptTemplate updateTemplate(UUID templateId, UUID userId, boolean isAdmin, PromptTemplate changes) {
        PromptTemplate existing = requireModifiable(templateId, userId, isAdmin);
        if (existing.userId() != null && changes.category() != existing.category()) {
            requireRoomFor(existing.userId(), changes.category());
        }
        PromptTemplate toSave = new PromptTemplate(existing.id(), existing.userId(),
                changes.name(), changes.category(), changes.description(),
                personaFor(changes.category(), changes.systemPrompt()), changes.userPrompt(),
                changes.outputConstraints(),
                changes.isPublic(), existing.parentTemplateId(),
                existing.versionNumber() + 1, existing.createdAt(), null, existing.isSystem(),
                changes.tags() != null ? changes.tags() : existing.tags(), existing.usageCount(),
                existing.isProtected(), existing.isDefault(), existing.explainerKey());
        return repo.save(toSave);
    }

    @Override
    public void deleteTemplate(UUID templateId, UUID userId, boolean isAdmin) {
        requireModifiable(templateId, userId, isAdmin);
        repo.deleteById(templateId);
    }

    /**
     * A section template is applied under the CV persona, never as one, so a system prompt sent
     * with it would be stored but silently ignored. Drop it instead of keeping dead text.
     */
    private static String personaFor(PromptCategory category, String systemPrompt) {
        return category != null && category.isCvSection() ? null : systemPrompt;
    }

    private void requireRoomFor(UUID userId, PromptCategory category) {
        String key = category != null ? category.name() : null;
        if (repo.countOwned(userId, key) >= MAX_OWN_TEMPLATES_PER_CATEGORY) {
            throw new IllegalStateException("You already have " + MAX_OWN_TEMPLATES_PER_CATEGORY
                    + " prompts of this type. Delete one to make room.");
        }
    }

    /**
     * Protected templates are app-origin and admin-only; everything else belongs to its creator.
     *
     * <p>The gate is protection rather than "is a system template", because those are now
     * different things: a user who wants a house prompt changed duplicates it and edits the copy,
     * which leaves the original intact for everyone else and for their own fallback. The error
     * says so, since "you cannot edit this" without "here is what to do instead" is a dead end.
     */
    private PromptTemplate requireModifiable(UUID templateId, UUID userId, boolean isAdmin) {
        PromptTemplate existing = repo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        if (existing.isProtected()) {
            if (!isAdmin) {
                throw new SecurityException(
                        "This prompt ships with the app and cannot be edited or deleted. "
                                + "Duplicate it to make a version of your own.");
            }
        } else if (!userId.equals(existing.userId())) {
            throw new SecurityException("You can only change your own templates");
        }
        return existing;
    }

    // ── Default selection ─────────────────────────────────────────────────────

    @Override
    public java.util.Optional<PromptTemplate> getDefault(UUID userId, PromptCategory category) {
        return repo.findDefaultFor(userId, category.name());
    }

    @Override
    public void selectDefault(UUID userId, PromptCategory category, UUID templateId) {
        PromptTemplate template = repo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        // Someone else's private template is not theirs to make their default.
        if (!template.isVisibleTo(userId)) {
            throw new SecurityException("You can only default to your own templates or the app's");
        }
        if (template.category() != category) {
            throw new IllegalArgumentException(
                    "That template is a " + template.category() + " prompt, not " + category);
        }
        repo.setUserDefault(userId, category.name(), templateId);
    }

    @Override
    public void resetDefault(UUID userId, PromptCategory category) {
        repo.clearUserDefault(userId, category.name());
    }

    @Override
    public PromptTemplate duplicate(UUID templateId, UUID userId, String newName) {
        PromptTemplate original = repo.findById(templateId)
                .filter(t -> t.isVisibleTo(userId))
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        requireRoomFor(userId, original.category());
        // The copy drops the app's explainer: it explains the original's wording, and the copy
        // exists to be edited away from it. The owner's description explains it from here on.
        PromptTemplate copy = new PromptTemplate(null, userId,
                newName != null ? newName : original.name() + " (copy)",
                original.category(), original.description(), original.systemPrompt(),
                original.userPrompt(), original.outputConstraints(), false,
                original.id(), original.versionNumber() + 1, null, null, false,
                original.tags(), 0, false, false, null);
        return repo.save(copy);
    }
}
