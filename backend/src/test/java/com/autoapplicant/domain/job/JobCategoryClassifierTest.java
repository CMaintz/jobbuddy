package com.autoapplicant.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Governance, risk and compliance work is advertised under three unrelated source categories:
 * Jobindex files it as IT security, as "Sikkerhed" (which is mostly guards) or as compliance under
 * law. The category is a hard feed filter, so where it lands decides whether someone looking for
 * GRC work ever sees the posting, and whether an engineer or a lawyer still sees theirs.
 */
class JobCategoryClassifierTest {

    private final JobCategoryClassifier classifier = new JobCategoryClassifier();

    @ParameterizedTest(name = "{0} [{1}]")
    @CsvSource(delimiter = '|', textBlock = """
            Informationssikkerhedskoordinator til Aarhus Kommune | it-sikkerhed
            Informationssikkerhedskonsulent                      | sikkerhed
            Awareness-konsulent                                  | it-sikkerhed
            Awareness-koordinator til NIS2-implementering        | it-sikkerhed
            GRC-konsulent til voksende rådgivningshus            | it
            NIS2-koordinator                                     | it-projektledelse
            NIS2-projektleder                                    | it-projektledelse
            Compliance Officer                                   | jura
            Compliancekonsulent                                  | finans
            Databeskyttelseskoordinator                          | jura
            Beredskabskoordinator til Center for Teknik og Miljø | sikkerhed
            """)
    void an_unambiguous_grc_title_beats_the_source_category(String title, String sourceCategory) {
        assertThat(classifier.classify(List.of(sourceCategory), title, null))
                .isEqualTo(JobCategory.GRC_COMPLIANCE);
    }

    @ParameterizedTest(name = "{0} [{1}] -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            Credit Risk Manager                    | finans       | FINANCE
            Kreditrisikostyring                    | finans       | FINANCE
            IT-sikkerhedsspecialist, NIS2          | it-sikkerhed | SOFTWARE_IT
            Jurist med speciale i GDPR             | jura         | LEGAL
            Advokat - compliance og persondataret  | jura         | LEGAL
            """)
    void a_title_that_only_touches_grc_keeps_its_source_category(
            String title, String sourceCategory, JobCategory expected) {
        assertThat(classifier.classify(List.of(sourceCategory), title, null)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Information Security Consultant
            Information Security Director
            Compliance Director
            Risikostyring og informationssikkerhed, fuldmægtig
            ISO/IEC 27001 Lead Implementer
            """)
    void grc_titles_without_a_source_category_land_in_grc(String title) {
        assertThat(classifier.classify(List.of(), title, null)).isEqualTo(JobCategory.GRC_COMPLIANCE);
    }

    @Test
    void cto_is_matched_as_a_word_not_inside_director() {
        assertThat(classifier.classify(List.of(), "Sales Director", null)).isEqualTo(JobCategory.SALES);
        assertThat(classifier.classify(List.of(), "CTO til scaleup", null)).isEqualTo(JobCategory.SOFTWARE_IT);
    }

    @Test
    void a_neutral_title_is_classified_grc_from_the_posting_body() {
        String body = "Vil du være med til at løfte kommunens informationssikkerhed? Vi søger en "
                + "kollega, der kan drive vores arbejde med NIS2, ISO 27001 og awareness-træning "
                + "af medarbejderne.";

        assertThat(classifier.classify(List.of(), "Konsulent til Digitalisering og IT", body))
                .isEqualTo(JobCategory.GRC_COMPLIANCE);
    }

    @Test
    void a_passing_mention_of_compliance_in_the_body_is_not_a_grc_job() {
        assertThat(classifier.classify(List.of(), "HR-konsulent",
                "Du står for rekruttering og onboarding og skal sikre compliance med GDPR i alle "
                        + "personalesager."))
                .isNotEqualTo(JobCategory.GRC_COMPLIANCE);
        assertThat(classifier.classify(List.of(), "QA-specialist",
                "Du sikrer GMP compliance i produktionen og dokumenterer afvigelser."))
                .isNotEqualTo(JobCategory.GRC_COMPLIANCE);
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @CsvSource(delimiter = '|', textBlock = """
            Sikkerhedsvagt til natlige runderinger             | sikkerhed
            Vagt til Københavns Lufthavn                       | sikkerhed
            Security Officer til lufthavnens sikkerhedskontrol | sikkerhed
            Dørmand til weekendvagter                          | sikkerhed
            """)
    void security_guard_postings_stay_out_of_grc(String title, String sourceCategory) {
        String body = "Som vagt sikrer du adgangskontrol, runderinger og overvågning af området. "
                + "Du har gennemført vagtuddannelsen og har et gyldigt vagtkort.";

        assertThat(classifier.classify(List.of(sourceCategory), title, body))
                .isEqualTo(JobCategory.OPERATIONS_LOGISTICS);
    }

    @Test
    void a_firefighter_with_a_beredskab_employer_is_not_grc() {
        assertThat(classifier.classify(List.of(), "Brandmand til Beredskab Fyn",
                "Som brandmand rykker du ud til brand og redning."))
                .isNotEqualTo(JobCategory.GRC_COMPLIANCE);
    }

    @Test
    void technical_security_and_development_stay_in_software() {
        assertThat(classifier.classify(List.of("it-sikkerhed"), "SOC-analytiker", null))
                .isEqualTo(JobCategory.SOFTWARE_IT);
        assertThat(classifier.classify(List.of(), "Backend Developer",
                "Du bygger vores betalingsplatform og sikrer, at den overholder GDPR."))
                .isEqualTo(JobCategory.SOFTWARE_IT);
    }

    @Test
    void brand_awareness_is_not_security_awareness() {
        assertThat(classifier.classify(List.of(), "Brand awareness manager", null))
                .isNotEqualTo(JobCategory.GRC_COMPLIANCE);
    }

    @Test
    void reclassifying_a_stored_job_moves_only_what_the_title_or_an_empty_category_justifies() {
        // The source category is not stored, so the stored category stands in for it.
        assertThat(classifier.reclassify(JobCategory.SOFTWARE_IT, "Informationssikkerhedskoordinator", null))
                .isEqualTo(JobCategory.GRC_COMPLIANCE);
        assertThat(classifier.reclassify(JobCategory.OTHER, "Konsulent",
                "Du driver kommunens arbejde med informationssikkerhed og NIS2."))
                .isEqualTo(JobCategory.GRC_COMPLIANCE);
        assertThat(classifier.reclassify(JobCategory.LEGAL, "Jurist med speciale i GDPR", null))
                .isEqualTo(JobCategory.LEGAL);
        assertThat(classifier.reclassify(JobCategory.SOFTWARE_IT, "IT-sikkerhedsspecialist, NIS2", null))
                .isEqualTo(JobCategory.SOFTWARE_IT);
        assertThat(classifier.reclassify(JobCategory.OPERATIONS_LOGISTICS, "Sikkerhedsvagt", null))
                .isEqualTo(JobCategory.OPERATIONS_LOGISTICS);
    }
}
