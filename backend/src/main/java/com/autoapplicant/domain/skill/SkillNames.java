package com.autoapplicant.domain.skill;

import java.util.Locale;

/**
 * The one way a skill name becomes a lookup key.
 *
 * <p>This exists because there used to be two. Every read path lowercased and trimmed, while
 * {@code createOrGet} slugified — {@code name.toLowerCase().replaceAll("[^a-z0-9]+", "-")} — so a
 * typed skill never found its seeded row and created a duplicate instead. Worse, the slug is lossy
 * in exactly the places skill names are not: {@code C#} and {@code C++} both became {@code c-},
 * so whichever was created second silently returned the other one's row.
 *
 * <p>Punctuation is therefore kept, not stripped. {@code c#}, {@code c++}, {@code .net},
 * {@code node.js} and {@code ci/cd} are distinct skills and must stay distinct keys.
 */
public final class SkillNames {

    private SkillNames() {}

    /** The lookup key for a skill name, or "" when there is no name to key on. */
    public static String normalize(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
