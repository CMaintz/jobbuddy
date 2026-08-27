package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.AtsCheck;
import com.autoapplicant.domain.document.structured.AtsReport;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
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
        AtsReport report = builder.forCoverage(50, List.of(), List.of(), "ATS",
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
    void nullFindingsAreTreatedAsClean() {
        assertThat(check(builder.basic("body", "ATS", null, null), "fact_guard"))
                .get().extracting(AtsCheck::status).isEqualTo("PASS");
    }
}
