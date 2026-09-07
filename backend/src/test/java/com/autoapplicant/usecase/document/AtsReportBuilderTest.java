package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.AtsCheck;
import com.autoapplicant.domain.document.structured.AtsReport;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.KeywordCoverage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AtsReportBuilderTest {

    private final AtsReportBuilder builder = new AtsReportBuilder();

    private static Optional<AtsCheck> check(AtsReport report, String code) {
        return report.checks().stream().filter(c -> c.code().equals(code)).findFirst();
    }

    @Test
    void cleanDocumentReportsPassingGuardChecks() {
        AtsReport report = builder.basic("A document body", "ATS", ContentGuardFindings.NONE, null);
        assertThat(check(report, "fact_guard")).get().extracting(AtsCheck::status).isEqualTo("PASS");
        assertThat(check(report, "filler_phrases")).get().extracting(AtsCheck::status).isEqualTo("PASS");
        // Most users have retracted nothing; a permanent PASS for that would be noise.
        assertThat(check(report, "retracted_claims")).isEmpty();
    }

    @Test
    void unsupportedMetricsFailWithTheOffendingClaims() {
        AtsReport report = builder.forCoverage(
                new KeywordCoverage(true, 50, List.of("Java"), List.of("Go"), List.of()), "ATS",
                new ContentGuardFindings(List.of("94772 users"), List.of(), List.of(), List.of()), null);
        assertThat(check(report, "fact_guard")).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo("FAIL");
            assertThat(c.detail()).contains("94772 users");
        });
    }

    @Test
    void fillerPhrasesWarnRatherThanFail() {
        AtsReport report = builder.basic("body", "ATS",
                new ContentGuardFindings(List.of(), List.of(), List.of("proven track record"), List.of()), null);
        assertThat(check(report, "filler_phrases")).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo("WARN");
            assertThat(c.detail()).contains("proven track record");
        });
    }

    @Test
    void retractedClaimsAppearOnlyWhenViolated() {
        AtsReport report = builder.basic("body", "ATS",
                new ContentGuardFindings(List.of(), List.of("led a team of 20"), List.of(), List.of()), null);
        assertThat(check(report, "retracted_claims")).get().extracting(AtsCheck::status).isEqualTo("FAIL");
    }

    @Test
    void findingListsAreCappedForDisplay() {
        List<String> many = List.of("a", "b", "c", "d", "e", "f", "g");
        AtsReport report = builder.basic("body", "ATS", new ContentGuardFindings(many, List.of(), List.of(), List.of()), null);
        assertThat(check(report, "fact_guard")).get().extracting(AtsCheck::detail)
                .asString().contains("(+2 more)").doesNotContain("\"g\"");
    }

    @Test
    void aDanishDocumentGetsDanishDiagnostics() {
        AtsReport danish = builder.basic("body", "ATS",
                new ContentGuardFindings(List.of(), List.of(), List.of("teamplayer"), List.of()), "Danish");
        assertThat(check(danish, "filler_phrases")).get().satisfies(c -> {
            assertThat(c.label()).isEqualTo("Floskler");
            assertThat(c.detail()).contains("Læses som fyld").contains("teamplayer");
        });
        // The code is the identifier, not the copy — it stays stable across languages.
        assertThat(check(danish, "real_text")).get().extracting(AtsCheck::label).isEqualTo("Rigtig tekst");
    }

    @Test
    void aFigureCountingSomethingElseWarnsRatherThanFails() {
        // The number is in the profile, just attached to another noun — usually a rewording.
        AtsReport report = builder.basic("body", "ATS",
                new ContentGuardFindings(List.of(), List.of(), List.of(), List.of("30 teams")), null);
        assertThat(check(report, "unverified_metrics")).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo("WARN");
            assertThat(c.detail()).contains("30 teams");
        });
        // …and the hard check stays green, because nothing was fabricated.
        assertThat(check(report, "fact_guard")).get().extracting(AtsCheck::status).isEqualTo("PASS");
    }

    @Test
    void a_measured_document_gets_a_keyword_line_reporting_what_it_covered() {
        AtsReport report = builder.forCoverage(
                new KeywordCoverage(true, 80, List.of("Java", "Kubernetes", "Go", "Terraform"),
                        List.of("Rust"), List.of()),
                "ATS", ContentGuardFindings.NONE, null);

        assertThat(check(report, "keyword_coverage")).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo("PASS");
            assertThat(c.detail()).contains("4 of 5").contains("80%");
        });
        assertThat(report.keywordCoverage()).isEqualTo(80);
        assertThat(report.missingKeywords()).containsExactly("Rust");
    }

    @Test
    void a_missing_requirement_warns_and_names_it_even_when_coverage_is_otherwise_high() {
        AtsReport report = builder.forCoverage(
                new KeywordCoverage(true, 75, List.of("Kubernetes"), List.of("Java"), List.of("Java")),
                "ATS", ContentGuardFindings.NONE, null);

        assertThat(check(report, "keyword_coverage")).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo("WARN");
            assertThat(c.detail()).contains("Java");
        });
    }

    @Test
    void thin_coverage_warns_even_with_no_requirement_missed() {
        AtsReport report = builder.forCoverage(
                new KeywordCoverage(true, 20, List.of("Go"), List.of("Java", "Rust", "C#"), List.of()),
                "ATS", ContentGuardFindings.NONE, null);

        assertThat(check(report, "keyword_coverage")).get().extracting(AtsCheck::status).isEqualTo("WARN");
    }

    @Test
    void an_unmeasured_document_gets_no_keyword_line_at_all() {
        // Nothing to measure against is not the same as covering nothing, so the report
        // stays quiet rather than showing a damning zero.
        AtsReport report = builder.basic("body", "ATS", ContentGuardFindings.NONE, null);

        assertThat(check(report, "keyword_coverage")).isEmpty();
        assertThat(report.keywordCoverage()).isZero();
    }

    @Test
    void the_keyword_line_speaks_danish_for_a_danish_document() {
        AtsReport report = builder.forCoverage(
                new KeywordCoverage(true, 90, List.of("Kubernetes"), List.of(), List.of()),
                "ATS", ContentGuardFindings.NONE, "Danish");

        assertThat(check(report, "keyword_coverage")).get().satisfies(c -> {
            assertThat(c.label()).isEqualTo("Nøgleord fra opslaget");
            assertThat(c.detail()).contains("nøgleord");
        });
    }

    @Test
    void nullFindingsAreTreatedAsClean() {
        assertThat(check(builder.basic("body", "ATS", null, null), "fact_guard"))
                .get().extracting(AtsCheck::status).isEqualTo("PASS");
    }
}
