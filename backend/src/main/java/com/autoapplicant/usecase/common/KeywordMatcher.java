package com.autoapplicant.usecase.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whole-token keyword matching. A plain {@code contains} is wrong for this: it reports
 * "Java" as present in a CV that only says "JavaScript", and "R" as present in almost
 * any text at all.
 *
 * <p>Boundaries are applied only where they mean something. A keyword that starts with
 * a letter or digit needs a boundary in front of it, so JavaScript does not satisfy
 * Java; one that starts with punctuation does not, so ASP.NET satisfies .NET. The same
 * rule at the end lets C# and C++ match without demanding a word character after the
 * symbol, while Node.js still refuses to match Node.jsx.
 *
 * <p>Matching is exact apart from case and the amount of whitespace inside a
 * multi-word keyword. There is deliberately no stemming: allowing a Danish plural or
 * definite suffix would let "he excels at" satisfy "Excel", and a keyword check that
 * over-reports is worse than one that misses an inflected form.
 */
public final class KeywordMatcher {

    /** Compiled patterns are reused across documents; the keyword set is small and repetitive. */
    private static final Map<String, Pattern> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
            return size() > 512;
        }
    };

    private KeywordMatcher() {}

    /** True when {@code text} contains {@code keyword} as a whole token. */
    public static boolean contains(String text, String keyword) {
        if (text == null || text.isBlank() || keyword == null || keyword.isBlank()) return false;
        return patternFor(keyword).matcher(text).find();
    }

    /** The keywords present in the text, in the order given, with duplicates and blanks dropped. */
    public static List<String> presentIn(String text, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();
        return keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .filter(k -> contains(text, k))
                .toList();
    }

    /** The keywords the text does not contain, in the order given. */
    public static List<String> absentFrom(String text, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();
        return keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .filter(k -> !contains(text, k))
                .toList();
    }

    private static Pattern patternFor(String keyword) {
        String key = keyword.trim().toLowerCase(Locale.ROOT);
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(key, KeywordMatcher::compile);
        }
    }

    private static Pattern compile(String keyword) {
        // Whitespace inside the keyword matches any run of whitespace in the document,
        // so "machine  learning" across a line break still counts.
        String body = Pattern.quote(keyword).length() > 0
                ? String.join("\\s+", java.util.Arrays.stream(keyword.split("\\s+"))
                        .map(Pattern::quote).toArray(String[]::new))
                : Pattern.quote(keyword);

        String prefix = startsAlphanumeric(keyword) ? "(?<![\\p{L}\\p{N}])" : "";
        String suffix = endsAlphanumeric(keyword) ? "(?![\\p{L}\\p{N}])" : "";
        return Pattern.compile(prefix + body + suffix,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static boolean startsAlphanumeric(String keyword) {
        return isAlphanumeric(keyword.charAt(0));
    }

    private static boolean endsAlphanumeric(String keyword) {
        return isAlphanumeric(keyword.charAt(keyword.length() - 1));
    }

    private static boolean isAlphanumeric(char c) {
        return Character.isLetterOrDigit(c);
    }

    /** Exposed for a guard that needs to report where a keyword was found. */
    public static int indexIn(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) return -1;
        Matcher m = patternFor(keyword).matcher(text);
        return m.find() ? m.start() : -1;
    }
}
