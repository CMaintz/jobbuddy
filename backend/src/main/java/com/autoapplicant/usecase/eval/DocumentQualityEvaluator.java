package com.autoapplicant.usecase.eval;

import com.autoapplicant.domain.document.QualityScore;
import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scores a generated application document against the posting and the profile it came from —
 * model-free, so the same document always scores the same.
 *
 * <p>This exists so prompt changes can be measured instead of eyeballed. Every dimension is a
 * property the Danish-market research names as decisive (see {@code danish_market_playbook.md}):
 * filler phrasing, proof anchored in real profile facts, posting-keyword coverage, whether the
 * letter merely retells the CV, invented numbers, and length discipline.
 *
 * <p>It reuses {@link ClicheGuard} and {@link DocumentFactGuard} rather than restating their
 * rules, so a phrase added to the guard is immediately reflected in the score.
 *
 * <p>What it deliberately does NOT do is judge whether the writing is any good. It measures the
 * mechanical properties that correlate with the failure modes; a document can score 100 and still
 * be dull. Use it to catch regressions, not to certify quality.
 */
@Service
public class DocumentQualityEvaluator {

    public static final String FILLER = "filler";
    public static final String EVIDENCE = "evidence";
    public static final String KEYWORDS = "keywords";
    public static final String CV_ECHO = "cv_echo";
    public static final String FACTS = "facts";
    public static final String LENGTH = "length";

    private static final Pattern WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");

    /** Tokens too common to count as evidence of anything. */
    private static final Set<String> STOPWORDS = Set.of(
            "og", "til", "med", "som", "der", "det", "den", "er", "har", "for", "the", "and",
            "with", "that", "this", "have", "has", "you", "your", "our", "from", "job", "role");

    private final ClicheGuard clicheGuard;
    private final DocumentFactGuard factGuard;

    public DocumentQualityEvaluator(ClicheGuard clicheGuard, DocumentFactGuard factGuard) {
        this.clicheGuard = clicheGuard;
        this.factGuard = factGuard;
    }

    /**
     * @param document        the generated letter body
     * @param profileJson     the contact-free profile the document was generated from
     * @param postingKeywords keywords the posting cares about (technologies, skills)
     * @param targetWords     the word target the prompt asked for; the band is ±35%
     */
    public QualityScore evaluate(String document, String profileJson,
                                 List<String> postingKeywords, int targetWords) {
        String text = document != null ? document : "";
        List<QualityScore.Dimension> dimensions = new ArrayList<>();
        dimensions.add(filler(text));
        dimensions.add(evidence(text, profileJson));
        dimensions.add(keywords(text, postingKeywords));
        dimensions.add(cvEcho(text, profileJson));
        dimensions.add(facts(text, profileJson));
        dimensions.add(length(text, targetWords));

        int weighted = dimensions.stream().mapToInt(d -> d.score() * d.weight()).sum();
        int totalWeight = dimensions.stream().mapToInt(QualityScore.Dimension::weight).sum();
        return new QualityScore(totalWeight == 0 ? 0 : Math.round((float) weighted / totalWeight), dimensions);
    }

    /**
     * Known floskler and AI-tells. Each one costs a quarter of the dimension.
     *
     * <p>Weighted highest of all dimensions: Danish advisers describe a floskel as an instant
     * reject rather than a blemish, so a letter full of them should not be rescued by scoring well
     * everywhere else.
     *
     * <p>Weight 0 on an empty document: a dimension that cannot be judged must not award points,
     * or a document with no content at all scores respectably for containing no mistakes.
     */
    private QualityScore.Dimension filler(String text) {
        if (judgeable(text)) {
            List<String> phrases = clicheGuard.audit(text).phrases();
            int score = Math.max(0, 100 - 25 * phrases.size());
            return new QualityScore.Dimension(FILLER, score, 5,
                    phrases.isEmpty() ? "No known filler phrases." : "Filler: " + phrases);
        }
        return new QualityScore.Dimension(FILLER, 0, 0, "Nothing to judge.");
    }

    /**
     * Proof anchored in the profile: distinct multi-character profile tokens (employers,
     * technologies, project names) that actually appear in the document. A letter that names
     * nothing concrete is the generic-letter failure.
     *
     * <p>This measures reference density, not whether the references earn their place — a
     * name-dropping letter scores as well as an argued one. {@link #CV_ECHO} is what separates them.
     */
    private QualityScore.Dimension evidence(String text, String profileJson) {
        Set<String> anchors = distinctTokens(profileJson);
        Set<String> used = new LinkedHashSet<>(distinctTokens(text));
        used.retainAll(anchors);
        long numbers = Arrays.stream(WORD.split(text.toLowerCase(Locale.ROOT)))
                .filter(t -> t.chars().anyMatch(Character::isDigit)).distinct().count();
        int score = Math.min(100, (int) (used.size() * 4 + numbers * 10));
        return new QualityScore.Dimension(EVIDENCE, score, 3,
                used.size() + " profile term(s) and " + numbers + " figure(s) referenced.");
    }

    /** Share of the posting's keywords the document actually mentions. */
    private QualityScore.Dimension keywords(String text, List<String> postingKeywords) {
        if (postingKeywords == null || postingKeywords.isEmpty()) {
            return new QualityScore.Dimension(KEYWORDS, 100, 0, "No posting keywords supplied.");
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        List<String> missing = postingKeywords.stream()
                .filter(k -> !haystack.contains(k.toLowerCase(Locale.ROOT))).toList();
        int hit = postingKeywords.size() - missing.size();
        return new QualityScore.Dimension(KEYWORDS,
                Math.round(100f * hit / postingKeywords.size()), 2,
                hit + "/" + postingKeywords.size() + " covered"
                + (missing.isEmpty() ? "." : "; missing " + missing + "."));
    }

    /**
     * The CV-retelling trap: sentences that are near-copies of profile text. Danish advisers name
     * this as the most common reason a letter adds nothing. Scored inversely — more echo, lower score.
     */
    private QualityScore.Dimension cvEcho(String text, String profileJson) {
        List<String> sentences = Arrays.stream(SENTENCE.split(text))
                .filter(s -> tokens(s).size() >= 6).toList();
        if (sentences.isEmpty()) {
            return new QualityScore.Dimension(CV_ECHO, 0, 0, "Too short to judge.");
        }
        Set<String> profileTokens = distinctTokens(profileJson);
        long echoed = sentences.stream().filter(s -> {
            // Content words only: function words ("og", "the", "i") are shared by every sentence
            // and would drown out the signal, letting a verbatim lift score as original.
            Set<String> content = distinctTokens(s);
            if (content.isEmpty()) return false;
            long overlap = content.stream().filter(profileTokens::contains).count();
            return (double) overlap / content.size() >= 0.6;   // most of it is lifted profile text
        }).count();
        int score = Math.round(100f * (sentences.size() - echoed) / sentences.size());
        return new QualityScore.Dimension(CV_ECHO, score, 2,
                echoed + "/" + sentences.size() + " sentence(s) largely restate the profile.");
    }

    /** Numbers the profile does not support — the one dimension where a single hit is severe. */
    private QualityScore.Dimension facts(String text, String profileJson) {
        if (!judgeable(text)) {
            return new QualityScore.Dimension(FACTS, 0, 0, "Nothing to judge.");
        }
        List<String> invented = factGuard.audit(text, profileJson != null ? profileJson : "").inventedMetrics();
        int score = invented.isEmpty() ? 100 : Math.max(0, 100 - 50 * invented.size());
        return new QualityScore.Dimension(FACTS, score, 3,
                invented.isEmpty() ? "Every figure traces to the profile." : "Unsupported: " + invented);
    }

    /** Distance from the requested word target; the band is generous, the falloff is not. */
    private QualityScore.Dimension length(String text, int targetWords) {
        int words = tokens(text).size();
        if (targetWords <= 0) {
            return new QualityScore.Dimension(LENGTH, 100, 0, words + " words; no target set.");
        }
        double drift = Math.abs(words - targetWords) / (double) targetWords;
        int score = drift <= 0.35 ? 100 : (int) Math.max(0, Math.round(100 - (drift - 0.35) * 200));
        return new QualityScore.Dimension(LENGTH, score, 1,
                words + " words against a target of " + targetWords + ".");
    }

    /** Below this there is no document to score, only an absence — see {@link #filler}. */
    private static boolean judgeable(String text) {
        return tokens(text).size() >= 20;
    }

    private static List<String> tokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(WORD.split(text.toLowerCase(Locale.ROOT)))
                .filter(t -> !t.isBlank()).toList();
    }

    /** Distinct, meaningful tokens — the unit both the evidence and echo dimensions compare on. */
    private static Set<String> distinctTokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens(text)) {
            if (token.length() >= 4 && !STOPWORDS.contains(token)) result.add(token);
        }
        return result;
    }
}
