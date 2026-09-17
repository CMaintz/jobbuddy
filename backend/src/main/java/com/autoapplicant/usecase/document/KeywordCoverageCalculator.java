package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.JobKeywords;
import com.autoapplicant.domain.document.structured.KeywordCoverage;
import com.autoapplicant.usecase.common.KeywordMatcher;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures a generated document against the posting's own keywords.
 *
 * <p>This used to be the model's job: the prompt asked for keywordCoverage,
 * matchedKeywords and missingKeywords, and the numbers were displayed as if measured.
 * A model marking its own homework will report a keyword as covered that it never
 * wrote down, and nothing checked. Counting here instead makes the figure mean what
 * it says.
 *
 * <p>A requirement counts double: missing something the posting demanded is worse
 * than missing something it merely liked.
 */
@Component
public class KeywordCoverageCalculator {

    private static final int REQUIRED_WEIGHT = 2;
    private static final int PREFERRED_WEIGHT = 1;

    private final SkillCanonicalizer skillCanonicalizer;

    public KeywordCoverageCalculator(SkillCanonicalizer skillCanonicalizer) {
        this.skillCanonicalizer = skillCanonicalizer;
    }

    public KeywordCoverage measure(String documentText, JobKeywords keywords) {
        if (documentText == null || documentText.isBlank() || keywords == null || keywords.isEmpty()) {
            return KeywordCoverage.NOT_MEASURED;
        }

        Partition required = partition(documentText, keywords.required());
        Partition preferred = partition(documentText, keywords.preferred());

        int possible = required.total() * REQUIRED_WEIGHT + preferred.total() * PREFERRED_WEIGHT;
        if (possible == 0) return KeywordCoverage.NOT_MEASURED;

        int earned = required.matched().size() * REQUIRED_WEIGHT + preferred.matched().size() * PREFERRED_WEIGHT;

        return new KeywordCoverage(true, Math.round(100f * earned / possible),
                concat(required.matched(), preferred.matched()),
                concat(required.missing(), preferred.missing()),
                List.copyOf(required.missing()));
    }

    /** Required-then-preferred, in that order, as one unmodifiable list. */
    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    /**
     * Splits a keyword set into those the text covers and those it does not, in a single pass over
     * the posting's own wording (deduplicated, blanks dropped) — so each keyword is matched once,
     * not once to find the present and again to find the absent.
     */
    private Partition partition(String text, List<String> keywords) {
        if (keywords == null) return new Partition(List.of(), List.of());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .forEach(k -> (present(text, k) ? matched : missing).add(k));
        return new Partition(List.copyOf(matched), List.copyOf(missing));
    }

    /** A keyword set divided into the matched and missing forms, keeping the posting's wording. */
    private record Partition(List<String> matched, List<String> missing) {
        int total() {
            return matched.size() + missing.size();
        }
    }

    /**
     * A keyword counts as covered when the document mentions any of its surface forms — so a CV
     * that says "k8s" satisfies a "Kubernetes" requirement. This expands a curated alias set; it
     * is not stemming, so {@link KeywordMatcher}'s whole-token, no-inflection guarantee holds.
     */
    private boolean present(String text, String keyword) {
        for (String form : skillCanonicalizer.surfaceForms(keyword)) {
            if (KeywordMatcher.contains(text, form)) return true;
        }
        return false;
    }
}
