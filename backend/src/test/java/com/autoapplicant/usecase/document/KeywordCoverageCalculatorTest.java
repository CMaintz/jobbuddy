package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.JobKeywords;
import com.autoapplicant.domain.document.structured.KeywordCoverage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordCoverageCalculatorTest {

    private final KeywordCoverageCalculator calculator = new KeywordCoverageCalculator();

    private static final String CV = """
            Platform Engineer with ten years in production.
            Ran Kubernetes clusters and wrote Terraform for the whole estate.
            Comfortable in JavaScript on the frontend.
            """;

    @Test
    void a_keyword_the_document_never_says_is_missing_even_when_a_longer_word_contains_it() {
        // The CV says JavaScript, not Java. A contains() check would call this covered.
        KeywordCoverage coverage = calculator.measure(CV,
                new JobKeywords(List.of("Java"), List.of()));

        assertThat(coverage.matched()).isEmpty();
        assertThat(coverage.missing()).containsExactly("Java");
        assertThat(coverage.missingRequired()).containsExactly("Java");
        assertThat(coverage.percent()).isZero();
    }

    @Test
    void everything_asked_for_and_present_is_full_coverage() {
        KeywordCoverage coverage = calculator.measure(CV,
                new JobKeywords(List.of("Kubernetes"), List.of("Terraform")));

        assertThat(coverage.percent()).isEqualTo(100);
        assertThat(coverage.matched()).containsExactly("Kubernetes", "Terraform");
        assertThat(coverage.missing()).isEmpty();
    }

    @Test
    void a_missing_requirement_costs_twice_what_a_missing_preference_does() {
        // One required present, one preferred absent -> 2 of 3.
        KeywordCoverage requirementKept = calculator.measure(CV,
                new JobKeywords(List.of("Kubernetes"), List.of("Go")));
        // One required absent, one preferred present -> 1 of 3.
        KeywordCoverage requirementLost = calculator.measure(CV,
                new JobKeywords(List.of("Go"), List.of("Kubernetes")));

        assertThat(requirementKept.percent()).isEqualTo(67);
        assertThat(requirementLost.percent()).isEqualTo(33);
        assertThat(requirementLost.missingRequired()).containsExactly("Go");
        assertThat(requirementKept.missingRequired()).isEmpty();
    }

    @Test
    void a_posting_with_no_keywords_is_not_measured_rather_than_scored_zero() {
        // An unenriched job has nothing to check against. Reporting 0% would read as a
        // damning result for a document that was never actually assessed.
        assertThat(calculator.measure(CV, JobKeywords.NONE).measured()).isFalse();
        assertThat(calculator.measure(CV, null).measured()).isFalse();
        assertThat(calculator.measure(CV, new JobKeywords(List.of(), List.of())).measured()).isFalse();
    }

    @Test
    void an_empty_document_is_not_measured_either() {
        JobKeywords keywords = new JobKeywords(List.of("Java"), List.of());

        assertThat(calculator.measure("", keywords).measured()).isFalse();
        assertThat(calculator.measure(null, keywords).measured()).isFalse();
    }

    @Test
    void required_keywords_are_reported_before_preferred_ones() {
        KeywordCoverage coverage = calculator.measure(CV,
                new JobKeywords(List.of("Kubernetes"), List.of("Terraform")));

        assertThat(coverage.matched()).containsExactly("Kubernetes", "Terraform");
    }
}
