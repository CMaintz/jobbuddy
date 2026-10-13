package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.CvSectionPrompts;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Picks the section template that writes each CV section: the one the caller named, else the
 * user's chosen default for that section, else the app's. Shared by tailoring and per-field refine
 * so both write a section the same way.
 */
@Component
public class CvSectionTemplates {

    private final PromptTemplateRepositoryPort repo;

    public CvSectionTemplates(PromptTemplateRepositoryPort repo) {
        this.repo = repo;
    }

    /** Every section's default instructions, for a whole-CV generation. */
    public CvSectionPrompts defaultsFor(UUID userId) {
        Map<CvSection, String> prompts = new EnumMap<>(CvSection.class);
        for (CvSection section : CvSection.values()) {
            repo.findDefaultFor(userId, section.promptCategory().name())
                    .ifPresent(template -> prompts.put(section, template.userPrompt()));
        }
        return new CvSectionPrompts(prompts);
    }

    /**
     * The template for one section. An explicit id must be one the user may see and must belong to
     * that section, or a request could apply another user's private prompt or a cover-letter
     * persona to a CV field; either falls back to the section's default.
     */
    public Optional<PromptTemplate> forSection(UUID userId, CvSection section, UUID templateId) {
        Optional<PromptTemplate> chosen = Optional.ofNullable(templateId)
                .flatMap(repo::findById)
                .filter(t -> t.isVisibleTo(userId) && t.category() == section.promptCategory());
        Optional<PromptTemplate> resolved = chosen.isPresent()
                ? chosen
                : repo.findDefaultFor(userId, section.promptCategory().name());
        resolved.ifPresent(t -> repo.incrementUsage(t.id()));
        return resolved;
    }
}
