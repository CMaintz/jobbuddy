package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import com.autoapplicant.domain.user.WorkExperience;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Which experience asks a work history clearly cannot meet. The wordings follow real enriched
 * Danish postings; the work histories are invented.
 */
class RequiredExperienceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    /** Four years in comms, the last two current, one of them touching security awareness. */
    private static final List<WorkExperience> COMMS_HISTORY = List.of(
            role("Kommunikationsmedarbejder", "Kampagner, presse og awareness-kampagner om"
                    + " informationssikkerhed sammen med it-afdelingen",
                    LocalDate.of(2022, 6, 1), LocalDate.of(2024, 6, 1), false),
            role("Marketingkoordinator", "Nyhedsbreve, SEO-indhold og events",
                    LocalDate.of(2024, 6, 1), null, true));

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            5 års erfaring med at definere krav til sikkerhedsarkitektur | Kræver 5 års erfaring med sikkerhedsarkitektur
            Du har mindst 4 års erfaring fra en SOC                      | Kræver 4 års erfaring fra et SOC
            Mindst 4 års erfaring med penetrationstest                   | Kræver 4 års erfaring med penetrationstest
            4+ years of experience in security architecture              | Kræver 4 års erfaring med sikkerhedsarkitektur
            Minimum fem års erfaring som softwareudvikler                | Kræver 5 års erfaring som softwareudvikler
            4 or more years of experience as a software engineer         | Kræver 4 års erfaring som softwareudvikler
            4 eller flere års erfaring med penetrationstest              | Kræver 4 års erfaring med penetrationstest
            4-årig erfaring som softwareudvikler                         | Kræver 4 års erfaring som softwareudvikler
            At least four (4) years of experience in security architecture | Kræver 4 års erfaring med sikkerhedsarkitektur
            """)
    void a_senior_specialist_ask_a_history_without_any_technical_role_cannot_meet_is_unmet(
            String ask, String reason) {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);

        assertThat(gate.unmet(posting(required(ask)))).contains(reason);
    }

    /**
     * Asking for experience is normal, and a graduate applies to a junior ask on education
     * alone. Only a senior ask can put a post out of reach.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Erfaring med at definere krav til sikkerhedsarkitektur, der understøtter forretningen
            Du har erfaring fra en SOC
            Solid erfaring med penetrationstest
            Erfaring med softwareudvikling
            Flere års erfaring med softwareudvikling
            2 års erfaring med sikkerhedsarkitektur
            Mindst 3 års erfaring som softwareudvikler
            3+ years of experience in security architecture
            3\u20135 års erfaring med sikkerhedsarkitektur
            2\u20134 års erfaring fra et SOC
            3 til 5 års erfaring som softwareudvikler
            3 to 5 years of experience in security architecture
            Tre-fem års erfaring med penetrationstest
            3-5+ years of experience as a software engineer
            """)
    void an_ask_for_no_or_under_four_years_is_met(String ask) {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);

        assertThat(gate.unmet(posting(required(ask)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Mindst 5 års erfaring med penetrationstest er en fordel
            Gerne 5 års erfaring fra et SOC
            5+ years of experience with security architecture is nice to have
            5 års erfaring med overvågning af sikkerhedshændelser i et SOC eller lignende miljø
            5 års erfaring med fx sikkerhedsarkitektur eller cloud
            5 års erfaring med penetrationstest eller kommunikation
            Cirka 5-8 års erfaring med strategisk intern kommunikation
            Mindst 5 års erfaring med GDPR-rådgivning og juridisk sparring
            5 års kendskab til sikkerhedsarkitektur
            """)
    void an_ask_that_is_soft_open_or_outside_the_catalogue_is_met(String ask) {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);

        assertThat(gate.unmet(posting(required(ask)))).isEmpty();
    }

    /**
     * Incident response is part of a GRC and crisis-comms field, and sits on a course or skill
     * list rather than a job title, so a comms history may well have done it.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Mindst 5 års erfaring med incident response
            5 års erfaring med håndtering af sikkerhedshændelser (Incident Response)
            Mindst fem års erfaring med håndtering af it-sikkerhedshændelser
            """)
    void an_incident_response_ask_is_met_by_a_comms_history(String ask) {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);

        assertThat(gate.unmet(posting(required(ask)))).isEmpty();
    }

    /** Working beside developers, or in their team, is not being one. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            5 års erfaring med at arbejde tæt sammen med softwareudviklere
            5+ years of experience working in a software development team
            5 års erfaring fra et softwareudviklingsteam
            5 års erfaring fra det soc. område
            """)
    void an_ask_where_the_area_is_a_collaborator_or_lookalike_is_met(String ask) {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);

        assertThat(gate.unmet(posting(required(ask)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Mindst 4 års erfaring som softwareudvikler     | Kræver 4 års erfaring som softwareudvikler
            4 års erfaring med softwareudvikling           | Kræver 4 års erfaring som softwareudvikler
            Fire års erfaring med at udvikle software      | Kræver 4 års erfaring som softwareudvikler
            4+ years of experience as a software engineer  | Kræver 4 års erfaring som softwareudvikler
            """)
    void an_ask_to_have_developed_software_is_unmet_by_a_comms_history(String ask,
                                                                       String reason) {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);

        assertThat(gate.unmet(posting(required(ask)))).contains(reason);
    }

    /** Roles that work inside software delivery may well meet a development ask. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Product Owner
            Scrum Master
            Tech Lead
            QA Tester
            UX Designer
            Data Scientist
            IT-projektleder
            """)
    void a_role_inside_software_delivery_meets_a_development_ask(String title) {
        RequiredExperience gate = RequiredExperience.forHistory(List.of(
                role(title, "", LocalDate.of(2020, 1, 1), null, true)), TODAY);

        assertThat(gate.unmet(posting(required("Mindst 4 års erfaring med agil softwareudvikling"))))
                .isEmpty();
    }

    /** An education ask is checked against the degree, never against the work history. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            5 års uddannelsesmæssig baggrund i softwareudvikling | EDUCATION
            5 års uddannelsesmæssig baggrund i softwareudvikling | EXPERIENCE
            5 års erfaring med softwareudvikling                 | EDUCATION
            5 års erfaring med softwareudvikling                 | SKILL
            """)
    void an_ask_that_is_not_an_experience_ask_is_left_to_other_checks(String ask,
                                                                     RequirementKind kind) {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);
        JobRequirement requirement = new JobRequirement(ask, RequirementTier.REQUIRED, kind,
                null);

        assertThat(gate.unmet(posting(requirement))).isEmpty();
    }

    @Test
    void an_ask_the_posting_only_prefers_is_met() {
        RequiredExperience gate = RequiredExperience.forHistory(COMMS_HISTORY, TODAY);
        JobRequirement preferred = new JobRequirement("Mindst 5 års erfaring med penetrationstest",
                RequirementTier.PREFERRED, RequirementKind.EXPERIENCE, null);

        assertThat(gate.unmet(posting(preferred))).isEmpty();
    }

    /**
     * An engineer moving into security may well define security architecture or work in a SOC;
     * nothing in the history says otherwise, so the ask stays open to them.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Mindst 5 års erfaring med at definere krav til sikkerhedsarkitektur
            Du har mindst 5 års erfaring fra en SOC
            """)
    void any_technical_role_leaves_a_security_ask_open(String ask) {
        RequiredExperience gate = RequiredExperience.forHistory(List.of(
                role("Senior Software Engineer", "Java backends on Kubernetes",
                        LocalDate.of(2019, 1, 1), null, true)), TODAY);

        assertThat(gate.unmet(posting(required(ask)))).isEmpty();
    }

    @Test
    void a_role_named_only_in_the_description_counts_toward_the_area() {
        RequiredExperience gate = RequiredExperience.forHistory(List.of(
                role("Konsulent", "Penetrationstest af kunders webapplikationer",
                        LocalDate.of(2023, 1, 1), null, true)), TODAY);

        assertThat(gate.unmet(posting(required("Mindst 6 års erfaring med penetrationstest"))))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            2024-06-01 | 2026-06-01 | false | 4 |
            2023-06-01 | 2025-06-01 | false | 5 | Kræver 5 års erfaring som softwareudvikler
            2024-06-01 |            | true  | 4 |
            2025-06-01 |            | true  | 4 | Kræver 4 års erfaring som softwareudvikler
            """)
    void a_related_role_meets_a_years_ask_from_half_the_years_on(LocalDate start, LocalDate end,
                                                                 boolean current, int years,
                                                                 String reason) {
        RequiredExperience gate = RequiredExperience.forHistory(List.of(
                role("Softwareudvikler", "", start, end, current)), TODAY);

        assertThat(gate.unmet(posting(required(
                "Mindst " + years + " års erfaring som softwareudvikler"))))
                .isEqualTo(Optional.ofNullable(reason));
    }

    @Test
    void years_from_separate_related_roles_add_up() {
        RequiredExperience gate = RequiredExperience.forHistory(List.of(
                role("Webudvikler", "", LocalDate.of(2023, 6, 1), LocalDate.of(2024, 6, 1), false),
                role("Backend Developer", "", LocalDate.of(2024, 6, 1), LocalDate.of(2025, 6, 1),
                        false)), TODAY);

        assertThat(gate.unmet(posting(required("Mindst 4 års erfaring som softwareudvikler"))))
                .isEmpty();
    }

    @Test
    void a_related_role_without_dates_meets_any_years_ask() {
        RequiredExperience gate = RequiredExperience.forHistory(List.of(
                role("Softwareudvikler", "", null, null, false)), TODAY);

        assertThat(gate.unmet(posting(required("Mindst 10 års erfaring som softwareudvikler"))))
                .isEmpty();
    }

    @Test
    void a_profile_with_no_work_history_is_unknown_not_lacking() {
        RequiredExperience gate = RequiredExperience.forHistory(List.of(), TODAY);

        assertThat(gate.unmet(posting(required("Mindst 5 års erfaring med penetrationstest"))))
                .isEmpty();
    }

    private static WorkExperience role(String title, String description, LocalDate start,
                                       LocalDate end, boolean current) {
        return new WorkExperience(UUID.randomUUID(), USER, "Firma", title, null, description,
                start, end, current, List.of(), List.of(), 0, null, null, List.of());
    }

    private static JobRequirement required(String text) {
        return new JobRequirement(text, RequirementTier.REQUIRED, RequirementKind.EXPERIENCE,
                null);
    }

    private static Job posting(JobRequirement ask) {
        UUID id = UUID.randomUUID();
        return Job.builder().id(id).url("https://example.com/" + id).title("Stilling")
                .isActive(true).requirements(List.of(ask)).build();
    }
}
