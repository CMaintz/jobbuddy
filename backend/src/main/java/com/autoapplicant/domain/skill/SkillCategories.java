package com.autoapplicant.domain.skill;

import java.util.Locale;
import java.util.Set;

/**
 * What a skill's taxonomy category means downstream.
 *
 * <p>Skills used to be stored as two arrays — {@code skills} and {@code technologies} — which was
 * the only thing recording whether something was a tool or a way of working. Now that every skill
 * is one row carrying its category, that distinction is derivable rather than stored, and the
 * split of one list into two happens here.
 *
 * <p>The vocabulary is the seeded taxonomy's own (V012/V017, renamed in V022). The frontend keeps
 * an identical set for its profile chip grouping; if one changes, the other has to.
 */
public final class SkillCategories {

    private SkillCategories() {}

    /** Categories naming a tool, platform or technique — as opposed to a way of working. */
    private static final Set<String> TECHNICAL = Set.of(
            "programming language", "language", "framework", "library", "database", "cloud",
            "devops", "tool", "api", "ai/ml", "architecture", "testing", "security");

    public static boolean isTechnical(String category) {
        if (category == null || category.isBlank()) return false;
        return TECHNICAL.contains(category.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Whether this category should be listed on a CV at all as a named skill.
     *
     * <p>Soft skills are the exception the CV persona is explicit about: they belong woven into
     * the experience descriptions, never in a list. They stay on the profile — they are true, and
     * the model needs to know them to weave them — they just do not become chips.
     */
    public static boolean isSoftSkill(String category) {
        return category != null && "soft skill".equals(category.strip().toLowerCase(Locale.ROOT));
    }
}
