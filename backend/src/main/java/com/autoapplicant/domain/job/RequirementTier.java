package com.autoapplicant.domain.job;

/** Whether the posting demands something or merely likes it. */
public enum RequirementTier {
    REQUIRED,
    PREFERRED;

    public static RequirementTier parse(String value) {
        if (value == null) return PREFERRED;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // An ask we cannot tier is a preference: never invent a demand.
            return PREFERRED;
        }
    }
}
