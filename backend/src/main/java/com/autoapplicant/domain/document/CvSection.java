package com.autoapplicant.domain.document;

import java.util.Optional;

/**
 * The CV sections a user can attach standing "how I want this written" guidance to. The {@code key}
 * is the stable wire/storage identifier (matches the frontend section vocabulary and the tailoring
 * output fields); the display name is an i18n concern, not stored here — e.g. SKILLS shows as
 * "Competencies".
 */
public enum CvSection {
    PROFILE("profile"),
    SKILLS("skills"),
    EXPERIENCE("experience"),
    PROJECTS("projects"),
    EDUCATION("education"),
    CERTIFICATIONS("certifications");

    private final String key;

    CvSection(String key) {
        this.key = key;
    }

    public String key() {
        return key;
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
