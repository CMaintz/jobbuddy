package com.autoapplicant.usecase.document;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Figures a letter quotes from the posting about the employer ("I har ca. 680 medarbejdere"). The
 * profile cannot hold these, so without this exemption every needs-based letter reads as invented.
 *
 * <p>Claims carry no subject, so who a figure is about is read from its sentence. A sentence is
 * the employer's only when it names the employer (an address such as "jeres" or "your", a word such
 * as "kommunen", or the company's name) and has no first-person word. A figure is exempt only when
 * every sentence carrying it is the employer's. A pronoun-less sentence is as often the
 * candidate's ("Har 5 års erfaring") as the employer's, so it stays checked; so does an employer
 * sentence that also says "jeg". Both errors are false warnings, never a passed fabrication.
 */
final class PostingFigures {

    /**
     * Splits only before a capital, so "ca. 680" and "1.200" never end a sentence: a split there
     * would cut "jeg" off the figure and pass the candidate's claim as the employer's.
     */
    private static final Pattern SENTENCE_END = Pattern.compile(
            "(?<=[.!?])\\s+(?=\\p{Lu})", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Set<String> FIRST_PERSON = Set.of(
            "jeg", "mig", "min", "mit", "mine", "vi", "os", "vores",
            "me", "my", "we", "us", "our", "ours");

    private static final Set<String> EMPLOYER_WORDS = Set.of(
            "jer", "jeres", "virksomheden", "organisationen", "kommunen", "afdelingen", "teamet",
            "you", "your");

    /**
     * Matched case-sensitively, since lowercase "i" is the Danish "in". In Danish text capital "I"
     * addresses the employer; in English it is the candidate.
     */
    private static final Pattern CAPITAL_I = Pattern.compile("\\bI\\b");

    private static final Pattern WORD = Pattern.compile("\\p{L}+", Pattern.UNICODE_CHARACTER_CLASS);

    private PostingFigures() {}

    /** Normalized claims of {@code document} that are the posting's own figures, said of the employer. */
    static Set<String> quotedAboutEmployer(String document, EmployerPosting posting) {
        if (document == null || posting == null || posting.isBlank()) return Set.of();
        Set<String> employer = employerOnlyClaims(document, Reader.of(document, posting.companyName()));
        employer.retainAll(DocumentFactGuard.countClaims(posting.text()));
        return employer;
    }

    /** Claims only employer sentences make: one mention anywhere else keeps the figure checked. */
    private static Set<String> employerOnlyClaims(String document, Reader reader) {
        Set<String> employer = new HashSet<>();
        Set<String> elsewhere = new HashSet<>();
        for (String sentence : sentences(document)) {
            Set<String> claims = DocumentFactGuard.metricClaims(sentence);
            (reader.aboutEmployer(sentence) ? employer : elsewhere).addAll(claims);
        }
        employer.removeAll(elsewhere);
        return employer;
    }

    /** Line breaks are not sentence ends: a wrapped "Jeg har ledet / 680 medarbejdere" is one claim. */
    private static List<String> sentences(String document) {
        return SENTENCE_END.splitAsStream(DocumentFactGuard.stripMarkup(document)).toList();
    }

    /** Reads one letter's sentences for who they are about, given its language and the company. */
    private record Reader(boolean danish, Pattern companyName) {

        static Reader of(String document, String companyName) {
            // "I" itself is left out of the language vote: it lowercases to the English "i" and
            // would tip a short Danish "I har ..." over to English.
            String text = CAPITAL_I.matcher(DocumentFactGuard.stripMarkup(document)).replaceAll(" ");
            return new Reader(DocumentFactGuard.readsAsDanish(text), namePattern(companyName));
        }

        private static Pattern namePattern(String companyName) {
            if (companyName == null || companyName.isBlank()) return null;
            return Pattern.compile("(?<!\\p{L})" + Pattern.quote(companyName.strip()) + "(?!\\p{L})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }

        boolean aboutEmployer(String sentence) {
            boolean capitalI = CAPITAL_I.matcher(sentence).find();
            if (hasWord(sentence, FIRST_PERSON) || (capitalI && !danish)) return false;
            return (capitalI && danish) || hasWord(sentence, EMPLOYER_WORDS) || namesCompany(sentence);
        }

        private boolean namesCompany(String sentence) {
            return companyName != null && companyName.matcher(sentence).find();
        }
    }

    private static boolean hasWord(String sentence, Set<String> words) {
        Matcher m = WORD.matcher(sentence);
        while (m.find()) {
            if (words.contains(m.group().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
