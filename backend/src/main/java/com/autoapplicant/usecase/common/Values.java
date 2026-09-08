package com.autoapplicant.usecase.common;

import java.util.List;

/**
 * The two null-handling helpers that had been written out three and four times respectively.
 *
 * <p>They are trivial, which is exactly why they kept being rewritten rather than reached for.
 * Identical copies carry no risk of drifting apart, unlike the skill-name normalisation that did —
 * this is tidiness, not correctness.
 */
public final class Values {

    private Values() {}

    /** The list, or an empty one — never null, so callers can stream without checking. */
    public static <T> List<T> listOrEmpty(List<T> values) {
        return values != null ? values : List.of();
    }

    /** The trimmed text, or null when there was nothing but whitespace. */
    public static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.strip() : null;
    }

    public static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
