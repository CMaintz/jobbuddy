package com.autoapplicant.domain.document;

import java.util.Map;

/**
 * The per-section writing instructions for one CV generation: the body of whichever section
 * template the user has chosen for each section. Distinct from {@link WritingProfile}, which is an
 * analysed style fingerprint; these are prompts the user picked or wrote.
 */
public record CvSectionPrompts(Map<CvSection, String> prompts) {

    public static final CvSectionPrompts EMPTY = new CvSectionPrompts(Map.of());

    public CvSectionPrompts {
        prompts = prompts == null ? Map.of() : Map.copyOf(prompts);
    }

    /** The instruction for one section, or null when none applies. */
    public String forSection(CvSection section) {
        return prompts.get(section);
    }

    public boolean isEmpty() {
        return prompts.isEmpty();
    }
}
