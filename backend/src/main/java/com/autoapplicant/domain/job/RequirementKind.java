package com.autoapplicant.domain.job;

/**
 * What sort of ask this is. The point of the distinction is verification: each kind has a
 * different way of being checked, and two of them have none at all.
 *
 * <p>Only {@link #SKILL} feeds keyword coverage. Mixing "5 års erfaring" into a keyword
 * percentage would produce a number that looks measured and is not.
 */
public enum RequirementKind {
    /** A named tool, technology or competency — checkable against the document's text. */
    SKILL,
    /** A quantity of experience ("5 års erfaring med backend"). Surfaced, never scored. */
    EXPERIENCE,
    /** A degree or field of study. */
    EDUCATION,
    /** A spoken or written language, usually with a level. */
    LANGUAGE,
    /** A named certification or licence. */
    CERTIFICATION,
    /** Anything else — a driving licence, willingness to travel, a security clearance. */
    OTHER;

    public static RequirementKind parse(String value) {
        if (value == null) return OTHER;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    /** True when this kind can be checked by looking for words in the document. */
    public boolean isKeywordCheckable() {
        return this == SKILL;
    }
}
