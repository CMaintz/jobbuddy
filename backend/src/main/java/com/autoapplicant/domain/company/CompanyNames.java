package com.autoapplicant.domain.company;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides when two sources name the same employer: Lever has a board slug ("veo"), Jobindex the
 * registered name ("Veo Technologies ApS").
 *
 * <p>Deliberately narrow. Only case, dots, commas and trailing words that name the legal form
 * are ignored, because a wrong merge files one employer's notes and history under another, while
 * a missed one only leaves a duplicate. Letters are never folded and words are never dropped from
 * the middle or front of a name.
 */
public final class CompanyNames {

    /** Trailing words that say what kind of company it is, not which one. */
    private static final Set<String> FORM_WORDS = Set.of(
            "a/s", "as", "aps", "ivs", "i/s", "k/s", "p/s", "amba", "fmba", "smba",
            "ab", "asa", "oy", "oyj", "gmbh", "ag", "bv", "nv", "sa", "sas",
            "ltd", "limited", "inc", "llc", "plc", "corp", "corporation",
            "technologies", "technology");

    private CompanyNames() {
    }

    /**
     * A key that is equal for two names exactly when they are taken to be the same employer.
     *
     * @return the lower-cased words of {@code name} without trailing form words; a name made only
     *     of form words keeps its first word, so it never collapses to an empty key; "" for null
     */
    public static String matchKey(String name) {
        List<String> words = words(name);
        int end = words.size();
        while (end > 1 && FORM_WORDS.contains(words.get(end - 1))) {
            end--;
        }
        return String.join(" ", words.subList(0, end));
    }

    /**
     * The first word of {@link #matchKey}. Every name with the same key starts with it once case,
     * dots and commas are ignored, so a store can narrow its candidates with it.
     */
    public static String firstWord(String name) {
        List<String> words = words(name);
        return words.isEmpty() ? "" : words.get(0);
    }

    private static List<String> words(String name) {
        String plain = name == null ? "" : name.toLowerCase(Locale.ROOT).replace(".", "");
        return Arrays.stream(plain.split("[\s,]+")).filter(word -> !word.isEmpty()).toList();
    }
}
