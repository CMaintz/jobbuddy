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
 * GRC work ever sees the posting.
 */
class JobCategoryClassifierTest {

    private final JobCategoryClassifier classifier = new JobCategoryClassifier();

    @ParameterizedTest(name = "{0} [{1}]")
    @CsvSource(delimiter = '|', textBlock = """
            Informationssikkerhedskoordinator til Aarhus Kommune | it-sikkerhed
            Awareness-specialist til NIS2-implementering         | it-sikkerhed
            NIS2-projektleder                                    | it-projektledelse
            Beredskabskoordinator til Center for Teknik og Miljø | sikkerhed
            Compliance Officer                                   | compliance
            GRC-konsulent til voksende rådgivningshus            | it
            Kommunikationsmedarbejder til security awareness     | kommunikation
            Databeskyttelsesrådgiver                             | jura
            Risikostyring og informationssikkerhed, fuldmægtig   | administration
            """)
    void grc_titles_beat_the_source_category(String title, String sourceCategory) {
        assertThat(classifier.classify(List.of(sourceCategory), title, null))
                .isEqualTo(JobCategory.GRC_COMPLIANCE);
    }

    @Test
    void a_neutral_title_is_classified_grc_from_the_posting_body() {
        String body = "Vil du være med til at løfte kommunens informationssikkerhed? Vi søger en "
                + "kollega, der kan drive vores arbejde med NIS2, ISO 27001 og awareness-træning "
                + "af medarbejderne.";

        assertThat(classifier.classify(List.of(), "Konsulent til Digitalisering og IT", body))
                .isEqualTo(JobCategory.GRC_COMPLIANCE);
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
    void brand_awareness_is_marketing_not_security_awareness() {
        assertThat(classifier.classify(List.of(), "Brand awareness manager", null))
                .isNotEqualTo(JobCategory.GRC_COMPLIANCE);
    }
}
