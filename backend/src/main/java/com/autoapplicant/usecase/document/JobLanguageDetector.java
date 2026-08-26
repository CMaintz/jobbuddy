package com.autoapplicant.usecase.document;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic Danish/English detection for job postings.
 *
 * <p>Generation prompts previously told the model to "write in the same language as the job
 * description when clear, otherwise Danish" — leaving the single most visible property of the
 * output to a guess the application could neither see nor act on. Detecting the posting language
 * here makes that decision explicit: the prompt gets a concrete language instruction, and the
 * resolved language also selects the market conventions block ({@link MarketConventions}).
 *
 * <p>Stop-word scoring on deliberately disjoint marker sets — a token counts for at most one
 * language — plus a bonus for the Danish-only letters æ/ø/å. Returns {@code null} rather than
 * guessing when the text is too short or the two scores are too close, so callers keep their
 * existing fallback.
 */
public final class JobLanguageDetector {

    public static final String DANISH = "Danish";
    public static final String ENGLISH = "English";

    /** Tokens that occur in Danish but not in English. Ambiguous tokens (for, i, en, at) are excluded. */
    private static final Set<String> DANISH_MARKERS = Set.of(
            "og", "til", "med", "som", "der", "det", "den", "er", "har", "ikke", "kan", "skal",
            "på", "af", "om", "ved", "hos", "dig", "din", "dine", "vores", "jer", "jeres", "vil",
            "være", "hvor", "eller", "også", "mere", "meget", "godt", "både", "samt",
            "stilling", "stillingen", "ansøgning", "ansøgningen", "ansøg", "erfaring", "arbejde",
            "virksomhed", "virksomheden", "medarbejder", "medarbejdere", "kolleger", "kollegaer",
            "opgaver", "kompetencer", "ansættelse", "løn", "frist", "arbejdsplads", "afdeling");

    /** Tokens that occur in English but not in Danish. */
    private static final Set<String> ENGLISH_MARKERS = Set.of(
            "the", "and", "with", "that", "this", "is", "are", "was", "have", "has", "will",
            "of", "about", "our", "your", "you", "we", "they", "from", "their", "who", "what",
            "role", "application", "experience", "work", "company", "team", "apply", "deadline",
            "skills", "requirements", "responsibilities", "position", "candidate", "salary",
            "benefits", "opportunity", "join", "looking", "working");

    private static final Pattern TOKEN = Pattern.compile("[^\\p{L}]+");
    private static final Pattern DANISH_LETTERS = Pattern.compile("[æøåÆØÅ]");

    /** Below this many marker hits the sample is too thin to call. */
    private static final int MIN_HITS = 6;

    /** The winner must lead by this factor; anything closer is reported as unknown. */
    private static final double MIN_RATIO = 1.5;

    private JobLanguageDetector() {}

    /**
     * The language to write in: the user's explicit choice when they made one, otherwise the
     * posting's detected language, otherwise {@code null} (caller decides).
     */
    public static String resolve(String explicitLanguage, String jobDescription) {
        if (explicitLanguage != null && !explicitLanguage.isBlank()) return explicitLanguage.strip();
        return detect(jobDescription);
    }

    /** {@link #DANISH}, {@link #ENGLISH}, or {@code null} when the text does not clearly say. */
    public static String detect(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase(Locale.ROOT);

        int danish = 0;
        int english = 0;
        for (String token : TOKEN.split(lower)) {
            if (token.isEmpty()) continue;
            if (DANISH_MARKERS.contains(token)) danish++;
            else if (ENGLISH_MARKERS.contains(token)) english++;
        }
        // æ/ø/å appear in no English word; count them as (capped) Danish evidence so short
        // Danish postings still clear MIN_HITS.
        danish += Math.min(20, (int) DANISH_LETTERS.matcher(lower).results().count());

        if (danish + english < MIN_HITS) return null;
        if (danish >= english * MIN_RATIO) return DANISH;
        if (english >= danish * MIN_RATIO) return ENGLISH;
        return null;
    }

    /** True when {@code language} names Danish, in either English or Danish spelling. */
    public static boolean isDanish(String language) {
        if (language == null) return false;
        String normalized = language.strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("dansk") || normalized.startsWith("danish") || normalized.equals("da");
    }
}
