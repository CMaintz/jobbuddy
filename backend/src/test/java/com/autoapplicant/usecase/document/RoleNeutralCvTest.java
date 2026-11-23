package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.adapter.persistence.adapter.SkillTaxonomyPersistenceAdapter;
import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.CareerTargetRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.CustomSectionRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProfileStrengthRepositoryPort;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import com.autoapplicant.port.out.user.SpokenLanguageRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A communications and GRC profile, run through the real seeded taxonomy and the CV assembler.
 *
 * <p>The taxonomy decides which skills count as technologies and which heading each one gets, so
 * the seed data is part of what is under test here, not a fixture to stub out.
 */
@Import(SkillTaxonomyPersistenceAdapter.class)
class RoleNeutralCvTest extends PostgresIntegrationTest {

    /** Danish group headings that only make sense on an engineer's CV. */
    private static final List<String> ENGINEERING_HEADINGS = List.of(
            "Programmeringssprog", "Frameworks", "Biblioteker", "Databaser", "Cloud", "DevOps",
            "API'er", "AI/ML", "Arkitektur", "Test", "Sikkerhed");

    private static final List<String> COMPLIANCE_FRAMEWORKS = List.of("NIS2", "ISO 27001", "GDPR");

    @Autowired SkillTaxonomyRepositoryPort taxonomy;
    @Autowired JdbcTemplate jdbc;

    private final UUID userId = UUID.randomUUID();

    @Test
    void a_comms_and_grc_cv_carries_no_engineering_headings_and_no_technologies() {
        CareerProfileForAi source = profileWith(
                List.of("Kommunikationsstrategi", "NIS2", "ISO 27001", "GDPR", "Confluence",
                        "Communication"),
                List.of(commsRole()));

        StructuredDocument cv = assembleDanish(source);

        assertThat(skillHeadings(cv)).doesNotContainAnyElementsOf(ENGINEERING_HEADINGS);
        assertThat(headingOf(cv, "NIS2")).isEqualTo("Fagområde");
        assertThat(source.technologies()).doesNotContainAnyElementsOf(COMPLIANCE_FRAMEWORKS);
        assertThat(section(cv, "experience").items())
                .allSatisfy(role -> assertThat(role.technologies()).isEmpty());
    }

    @Test
    void an_engineers_security_tools_and_languages_keep_their_headings() {
        CareerProfileForAi source = profileWith(List.of("Java", "Keycloak"), List.of());

        StructuredDocument cv = assembleDanish(source);

        assertThat(headingOf(cv, "Java")).isEqualTo("Programmeringssprog");
        assertThat(headingOf(cv, "Keycloak")).isEqualTo("Sikkerhed");
        assertThat(source.technologies()).containsExactly("Java", "Keycloak");
    }

    @Test
    void the_seeded_plain_text_cv_prompts_do_not_assume_an_engineer() {
        List<String> prompts = jdbc.queryForList(
                "SELECT concat_ws(' ', description, system_prompt, user_prompt) FROM prompt_templates"
                        + " WHERE is_system AND explainer_key IN"
                        + " ('prompts.explainers.cvPlaintextTailoredEn',"
                        + " 'prompts.explainers.cvPlaintextTailoredDa')",
                String.class);

        assertThat(prompts).hasSize(2).allSatisfy(prompt -> assertThat(prompt.toLowerCase(Locale.ROOT))
                .doesNotContain("programming languages", "programmeringssprog", "technical skills",
                        "tekniske kompetencer", "devops"));
    }

    private StructuredDocument assembleDanish(CareerProfileForAi source) {
        CvDocumentAssembler assembler = new CvDocumentAssembler(mock(AtsReportBuilder.class),
                new KeywordCoverageCalculator(new SkillCanonicalizer(taxonomy)));
        return assembler.assemble(null, null, null, List.of(), source, null, "ATS", "cv-ats-classic",
                false, DocumentTheme.defaults(), null, "da", null, "");
    }

    /**
     * The profile as a CV import leaves it: skill rows with no category of their own, so every
     * heading comes from the taxonomy.
     */
    private CareerProfileForAi profileWith(List<String> skillNames, List<WorkExperience> roles) {
        ProfileSkillRepositoryPort skills = mock(ProfileSkillRepositoryPort.class);
        WorkExperienceRepositoryPort experience = mock(WorkExperienceRepositoryPort.class);
        when(skills.findByUserId(userId)).thenReturn(skillNames.stream()
                .map(name -> new ProfileSkill(UUID.randomUUID(), userId, name, null, "ADVANCED",
                        null, false, 0, null))
                .toList());
        when(experience.findByUserId(userId)).thenReturn(roles);
        return new CareerProfileContextService(mock(ProfileRepositoryPort.class), experience,
                mock(ProjectRepositoryPort.class), mock(EducationRepositoryPort.class),
                mock(CertificationRepositoryPort.class), skills, taxonomy,
                mock(SpokenLanguageRepositoryPort.class), mock(ProfileStrengthRepositoryPort.class),
                mock(CareerTargetRepositoryPort.class), mock(InterviewStoryRepositoryPort.class),
                mock(CustomSectionRepositoryPort.class), new ObjectMapper())
                .build(userId);
    }

    private WorkExperience commsRole() {
        return new WorkExperience(UUID.randomUUID(), userId, "Region Midt", "Kommunikationsmedarbejder",
                "Aarhus", "Intern kommunikation om informationssikkerhed og NIS2.",
                LocalDate.of(2022, 3, 1), null, true, List.of(),
                List.of("Planlagde en awareness-kampagne for 4.000 medarbejdere"), 0,
                Instant.now(), Instant.now(), List.of());
    }

    private static StructuredDocumentSection section(StructuredDocument cv, String id) {
        return cv.sections().stream().filter(s -> id.equals(s.id())).findFirst().orElseThrow();
    }

    private static List<String> skillHeadings(StructuredDocument cv) {
        return section(cv, "skills").items().stream()
                .map(StructuredDocumentItem::category)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String headingOf(StructuredDocument cv, String skill) {
        Map<String, String> bySkill = new java.util.HashMap<>();
        section(cv, "skills").items().forEach(item -> bySkill.put(item.title(), item.category()));
        return bySkill.get(skill);
    }
}
