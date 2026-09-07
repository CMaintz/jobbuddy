package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.JobKeywords;
import com.autoapplicant.domain.document.structured.KeywordCoverage;
import com.autoapplicant.usecase.common.KeywordMatcher;
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

    public KeywordCoverage measure(String documentText, JobKeywords keywords) {
        if (documentText == null || documentText.isBlank() || keywords == null || keywords.isEmpty()) {
            return KeywordCoverage.NOT_MEASURED;
        }

        List<String> matchedRequired = KeywordMatcher.presentIn(documentText, keywords.required());
        List<String> missingRequired = KeywordMatcher.absentFrom(documentText, keywords.required());
        List<String> matchedPreferred = KeywordMatcher.presentIn(documentText, keywords.preferred());
        List<String> missingPreferred = KeywordMatcher.absentFrom(documentText, keywords.preferred());

        int possible = (matchedRequired.size() + missingRequired.size()) * REQUIRED_WEIGHT
                + (matchedPreferred.size() + missingPreferred.size()) * PREFERRED_WEIGHT;
        if (possible == 0) return KeywordCoverage.NOT_MEASURED;

        int earned = matchedRequired.size() * REQUIRED_WEIGHT + matchedPreferred.size() * PREFERRED_WEIGHT;

        List<String> matched = new ArrayList<>(matchedRequired);
        matched.addAll(matchedPreferred);
        List<String> missing = new ArrayList<>(missingRequired);
        missing.addAll(missingPreferred);

        return new KeywordCoverage(true, Math.round(100f * earned / possible),
                List.copyOf(matched), List.copyOf(missing), List.copyOf(missingRequired));
    }
}
