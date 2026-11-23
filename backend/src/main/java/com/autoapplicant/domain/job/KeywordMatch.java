package com.autoapplicant.domain.job;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keyword lookup for the category classifier: a substring match, except for short acronyms.
 *
 * <p>Substring matching is what lets "udvikler" find "softwareudvikler", so it stays the rule. Two-
 * to four-letter acronyms are the exception: as substrings "cto" sits inside "director" (every
 * Sales Director became a software job) and "coo" inside "coordinator", so they must stand alone.
 */
final class KeywordMatch {

    private KeywordMatch() {}

    private static final Map<String, Pattern> WHOLE_WORD = Stream.of(
                    "cto", "ceo", "coo", "cfo", "sre", "sem", "seo", "bdr", "sdr", "grc")
            .collect(Collectors.toUnmodifiableMap(Function.identity(), k -> Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + Pattern.quote(k) + "(?![\\p{L}\\p{N}])")));

    static boolean contains(String lowerText, String keyword) {
        Pattern wholeWord = WHOLE_WORD.get(keyword);
        return wholeWord != null ? wholeWord.matcher(lowerText).find() : lowerText.contains(keyword);
    }
}
