package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A user's standing per-section instructions for CV tailoring — "how I want my profile /
 * competencies / experience written". One per user; injected into the tailoring prompt beside each
 * section, and reused by the per-field refine. Distinct from {@link WritingProfile}, which is an
 * analysed style fingerprint the app recomputes; these are authored by the user and never overwritten.
 */
public record CvSectionPrompts(UUID id, UUID userId, Map<CvSection, String> prompts, Instant updatedAt) {

    public CvSectionPrompts {
        prompts = prompts == null ? Map.of() : Map.copyOf(prompts);
    }

    public static CvSectionPrompts empty(UUID userId) {
        return new CvSectionPrompts(null, userId, Map.of(), null);
    }

    /** The user's instruction for one section, or null when they set none. */
    public String forSection(CvSection section) {
        return prompts.get(section);
    }

    public boolean isEmpty() {
        return prompts.isEmpty();
    }
}
