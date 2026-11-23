package com.autoapplicant.usecase.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.usecase.document.ClicheGuard;
import com.autoapplicant.usecase.document.DocumentFactGuard;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentQualityEvaluatorTest {

    private static final int TARGET_WORDS = 100;

    private final DocumentQualityEvaluator evaluator =
            new DocumentQualityEvaluator(new ClicheGuard(), new DocumentFactGuard());

    private int keywordScore(String document, List<String> keywords) {
        return evaluator.evaluate(document, "{}", keywords, TARGET_WORDS)
                .scoreOf(DocumentQualityEvaluator.KEYWORDS);
    }

    private int lengthScore(int words) {
        String document = String.join(" ", Collections.nCopies(words, "word"));
        return evaluator.evaluate(document, "{}", List.of(), TARGET_WORDS)
                .scoreOf(DocumentQualityEvaluator.LENGTH);
    }

    @Test
    void aKeywordInsideALongerWordIsNotCovered() {
        assertThat(keywordScore("Good algorithms, written in JavaScript.", List.of("Java", "Go")))
                .isZero();
    }

    @Test
    void symbolKeywordsAreCoveredWhenNamed() {
        assertThat(keywordScore("I build services in C# on ASP.NET.", List.of("C#", ".NET")))
                .isEqualTo(100);
    }

    @Test
    void lengthInsideTheBandScoresFull() {
        assertThat(lengthScore(135)).isEqualTo(100);
        assertThat(lengthScore(65)).isEqualTo(100);
    }

    @Test
    void lengthOutsideTheBandFallsOffTwoPointsPerPercent() {
        // 50% over target is 15 points past the 35% band, so 30 points lost.
        assertThat(lengthScore(150)).isEqualTo(70);
        assertThat(lengthScore(50)).isEqualTo(70);
        assertThat(lengthScore(300)).isZero();
    }
}
