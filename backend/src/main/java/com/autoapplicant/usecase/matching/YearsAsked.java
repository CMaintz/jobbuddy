package com.autoapplicant.usecase.matching;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The year count an experience ask names, in digits or Danish or English words. */
final class YearsAsked {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("en", 1), Map.entry("et", 1), Map.entry("one", 1),
            Map.entry("to", 2), Map.entry("two", 2), Map.entry("tre", 3), Map.entry("three", 3),
            Map.entry("fire", 4), Map.entry("four", 4), Map.entry("fem", 5),
            Map.entry("five", 5), Map.entry("seks", 6), Map.entry("six", 6),
            Map.entry("syv", 7), Map.entry("seven", 7), Map.entry("otte", 8),
            Map.entry("eight", 8), Map.entry("ni", 9), Map.entry("nine", 9),
            Map.entry("ti", 10), Map.entry("ten", 10));

    /** A year count in digits or words. */
    private static final String NUMBER =
            "(?:\\d{1,2}|(?:" + String.join("|", NUMBER_WORDS.keySet()) + ")\\b)";

    /**
     * The lowest number of years asked: "3 års", "3+ years", "fem års", "4-årig", "four (4)
     * years", "4 or more years", and a range written with a hyphen, an en or em dash, "til" or
     * "to" ("5-8 års", "3 til 5 års"). The range must be read whole: matching from its upper
     * bound would let a 3 to 5 years ask cap as a senior one.
     */
    private static final Pattern YEARS = Pattern.compile(
            "\\b(" + NUMBER + ")\\s*(?:\\(\\s*\\d{1,2}\\s*\\)\\s*)?\\+?\\s*"
                    + "(?:(?:[-\\u2013\\u2014]|\\btil\\b|\\bto\\b)\\s*" + NUMBER + "\\s*\\+?\\s*"
                    + "|(?:or more|eller flere|eller mere)\\s+)?"
                    + "(?:-?årig|års|år|years?)\\b",
            FLAGS);

    private YearsAsked() {}

    /** The lowest number of years {@code ask} names, empty when it names none. */
    static OptionalInt in(String ask) {
        Matcher m = YEARS.matcher(ask);
        if (!m.find()) return OptionalInt.empty();
        String n = m.group(1).toLowerCase(Locale.ROOT);
        Integer word = NUMBER_WORDS.get(n);
        return OptionalInt.of(word != null ? word : Integer.parseInt(n));
    }

    /** {@code ask} with its year count blanked out, wording such as "or more" included. */
    static String withoutCount(String ask) {
        return YEARS.matcher(ask).replaceFirst(" ");
    }
}
