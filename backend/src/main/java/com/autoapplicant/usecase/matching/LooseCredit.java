package com.autoapplicant.usecase.matching;

import com.autoapplicant.usecase.common.KeywordMatcher;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Partial credit for an ask the profile does not hold outright but very likely answers.
 *
 * <p>Three kinds, and an ask earns the best of them, never their sum:
 * <ul>
 *   <li>a held skill sharing its head word ({@link CompoundHeads}), or the ask named in the
 *       profile's own headline or summary: {@value #RELATED_CREDIT};
 *   <li>a held skill close in meaning: up to {@value #RELATED_CREDIT}, scaled by how close;
 *   <li>a suggested skill the user has not confirmed yet, named exactly or close in meaning:
 *       up to {@value #SUGGESTED_CREDIT}. Unconfirmed means unproven, so it earns half of what a
 *       related held skill does.
 * </ul>
 *
 * <p>Meaning is measured by the cosine of the two labels' embeddings. Short skill labels sit
 * close together even when unrelated ("Samarbejde" and "Pressearbejde" score 0.55), so nothing
 * below {@value #SIMILAR_FROM} counts, and credit grows linearly to its full value at
 * {@value #SIMILAR_FULL}. Two labels ending on the same generic word ("Leverandørvurdering",
 * "Risikovurdering") are close for that word alone and earn nothing by meaning. All of this is
 * loose evidence, which {@link SkillFitScorer} caps as a whole, so a posting full of near misses
 * cannot add up to a fit.
 *
 * <p>The thresholds were tuned on the eval's tuning rows with text-embedding-3-small, where
 * related pairs a reader would accept ("Risikostyring" and "Risikovurdering", 0.78) mostly sit
 * above 0.60 and the near misses of mismatched postings mostly below.
 */
final class LooseCredit {

    static final double RELATED_CREDIT = 0.5;
    static final double SUGGESTED_CREDIT = 0.25;
    static final double SIMILAR_FROM = 0.60;
    static final double SIMILAR_FULL = 0.75;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SkillCanonicalizer canonicalizer;
    private final List<String> heldLabels;
    private final List<String> heldHeads;
    private final List<String> suggestedLabels;
    private final Set<String> suggestedKeys;
    private final String ownWords;
    private final SkillSimilarity similarity;

    LooseCredit(SkillCanonicalizer canonicalizer, MatchSkills skills, String ownWords,
                SkillSimilarity similarity) {
        this.canonicalizer = canonicalizer;
        this.heldLabels = comparedForms(skills.held(), canonicalizer);
        this.heldHeads = heldLabels.stream().map(CompoundHeads::headOf).distinct().toList();
        this.suggestedLabels = comparedForms(skills.suggested(), canonicalizer);
        this.suggestedKeys = skills.suggested().stream().map(canonicalizer::canonical)
                .collect(Collectors.toUnmodifiableSet());
        this.ownWords = normalized(ownWords);
        this.similarity = similarity;
    }

    /**
     * Each label under every name the taxonomy knows it by. An ask is compared with all of them,
     * so all of them must be embedded. A CV import stores the English master name ("Public
     * Relations") where the user wrote "Pressearbejde", and English sits further from a Danish
     * ask in meaning ("Communication" scores 0.53 against "Intern kommunikation", where
     * "Kommunikation" scores 0.75) and shares no head word with it. Taking the best over every
     * name credits her the same whichever spelling she has.
     */
    static List<String> comparedForms(List<String> labels, SkillCanonicalizer canonicalizer) {
        return labels.stream().flatMap(label -> canonicalizer.surfaceForms(label).stream())
                .distinct().toList();
    }

    /** The loose credit for an ask the profile does not hold, 0 when nothing vouches for it. */
    double of(String ask) {
        double lexical = isRelated(ask) || isInOwnWords(ask) ? RELATED_CREDIT : 0;
        double meaning = RELATED_CREDIT * closeness(ask, heldLabels);
        double suggested = suggestedKeys.contains(canonicalizer.canonical(ask))
                ? SUGGESTED_CREDIT : SUGGESTED_CREDIT * closeness(ask, suggestedLabels);
        return Math.max(lexical, Math.max(meaning, suggested));
    }

    /** 0 below {@link #SIMILAR_FROM}, 1 from {@link #SIMILAR_FULL}, linear between. */
    private double closeness(String ask, List<String> labels) {
        double best = labels.stream()
                .filter(label -> !CompoundHeads.shareGenericHead(ask, label))
                .mapToDouble(label -> similarity.between(ask, label)).max().orElse(0);
        return Math.clamp((best - SIMILAR_FROM) / (SIMILAR_FULL - SIMILAR_FROM), 0, 1);
    }

    private boolean isRelated(String ask) {
        String head = CompoundHeads.headOf(ask);
        return heldHeads.stream().anyMatch(heldHead -> CompoundHeads.related(head, heldHead));
    }

    private boolean isInOwnWords(String ask) {
        return canonicalizer.surfaceForms(ask).stream()
                .anyMatch(form -> containsPhrase(ownWords, normalized(form)));
    }

    /**
     * Whole-phrase containment by plain search. The profile's own words are checked against every
     * distinct ask of every posting, and compiling a pattern per ask would churn the shared
     * {@link KeywordMatcher} cache for every other caller.
     */
    private static boolean containsPhrase(String text, String phrase) {
        if (phrase.isEmpty()) return false;
        for (int at = text.indexOf(phrase); at >= 0; at = text.indexOf(phrase, at + 1)) {
            int end = at + phrase.length();
            boolean startOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) return true;
        }
        return false;
    }

    private static String normalized(String text) {
        return WHITESPACE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
