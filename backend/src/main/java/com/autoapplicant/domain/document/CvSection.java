package com.autoapplicant.domain.document;

import java.util.Optional;

/**
 * The CV sections a user can choose a writing template for. The {@code key}
 * is the stable wire/storage identifier (matches the frontend section vocabulary and the tailoring
 * output fields); the display name is an i18n concern, not stored here — e.g. SKILLS shows as
 * "Competencies".
 */
public enum CvSection {
    PROFILE("profile", PromptCategory.CV_PROFILE),
    SKILLS("skills", PromptCategory.CV_SKILLS),
    EXPERIENCE("experience", PromptCategory.CV_EXPERIENCE),
    PROJECTS("projects", PromptCategory.CV_PROJECTS),
    EDUCATION("education", PromptCategory.CV_EDUCATION),
    CERTIFICATIONS("certifications", PromptCategory.CV_CERTIFICATIONS);

    private final String key;
    private final PromptCategory promptCategory;

    CvSection(String key, PromptCategory promptCategory) {
        this.key = key;
        this.promptCategory = promptCategory;
    }

    public String key() {
        return key;
    }

    /** The template category whose prompts write this section. */
    public PromptCategory promptCategory() {
        return promptCategory;
    }

    public static Optional<CvSection> forPromptCategory(PromptCategory category) {
        for (CvSection section : values()) {
            if (section.promptCategory == category) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }

    /** The section for a wire/storage key, or empty when the key is unknown (forward-compatible). */
    public static Optional<CvSection> fromKey(String key) {
        for (CvSection section : values()) {
            if (section.key.equals(key)) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }
}
