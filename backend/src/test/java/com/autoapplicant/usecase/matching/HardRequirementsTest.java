package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Certification;
import com.autoapplicant.domain.user.Education;
import com.autoapplicant.domain.user.Profile;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Which posting asks count as a hard requirement a profile clearly lacks. The wordings are taken
 * from real enriched Danish postings; the profiles are invented.
 */
class HardRequirementsTest {

    private static final UUID USER = UUID.randomUUID();
    private static final List<Certification> NO_CERTIFICATIONS = List.of();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Du er uddannet cand.jur.                       | EDUCATION     | Kræver cand.jur.
            cand.jur. eller cand.merc.jur.                 | EDUCATION     | Kræver cand.jur.
            Tømrer/snedker-uddannelse                      | EDUCATION     | Kræver en tømrer- eller snedkeruddannelse
            Uddannet pædagog                               | EDUCATION     | Kræver en pædagoguddannelse
            Du har en CISSP-certificering                  | CERTIFICATION | Kræver CISSP
            Du har en gyldig sikkerhedsgodkendelse         | OTHER         | Kræver sikkerhedsgodkendelse
            """)
    void a_named_credential_the_profile_lacks_is_unmet(String ask, RequirementKind kind,
                                                       String reason) {
        HardRequirements gate = gateFor(education("Bachelor", "Datalogi"), NO_CERTIFICATIONS);

        assertThat(gate.unmet(posting(required(ask, kind)))).contains(reason);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Du er uddannet cand.jur. eller tilsvarende                       | EDUCATION
            Relevant uddannelse (fx cand.mag., cand.comm., journalist)       | EDUCATION
            Relevant kandidatuddannelse inden for kommunikation eller jura   | EDUCATION
            Kørekort og egen bil er et krav                                  | CERTIFICATION
            Du har eller kan få en sikkerhedsgodkendelse                     | OTHER
            Du har eller opnår en sikkerhedsgodkendelse                      | OTHER
            Sikkerhedsgodkendelse er et krav                                 | OTHER
            5 års erfaring som jurist                                        | EXPERIENCE
            Cand.jur. er ønskeligt                                           | EDUCATION
            Du behøver ikke være jurist                                      | EDUCATION
            Uddannet jurist eller cand.polit.                                | EDUCATION
            """)
    void an_ask_that_leaves_a_way_in_is_met(String ask, RequirementKind kind) {
        HardRequirements gate = gateFor(education("Bachelor", "Datalogi"), NO_CERTIFICATIONS);

        assertThat(gate.unmet(posting(required(ask, kind)))).isEmpty();
    }

    @Test
    void a_degree_the_posting_only_prefers_is_met() {
        HardRequirements gate = gateFor(education("Bachelor", "Datalogi"), NO_CERTIFICATIONS);
        JobRequirement preferred = new JobRequirement("Du er uddannet cand.jur.",
                RequirementTier.PREFERRED, RequirementKind.EDUCATION, null);

        assertThat(gate.unmet(posting(preferred))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Kandidat            | Jura             | Du er uddannet cand.jur.
            Bachelor            | Erhvervsjuridisk | Du er uddannet cand.jur.
            Professionsbachelor | Sygepleje        | Autoriseret sygeplejerske
            Kandidat            | Dansk            | Du er uddannet cand.mag. i dansk
            Bachelor of Laws    | Law              | Du er uddannet cand.jur.
            LLB                 | International    | Du er uddannet cand.jur.
            Professionsbachelor | Socialpædagogik  | Uddannet pædagog
            Professionsbachelor | Dagtilbudspædagogik | Uddannet pædagog
            Professionsbachelor | Skole- og fritidspædagogik | Uddannet pædagog
            Uddannet            | Socialpædagog    | Uddannet pædagog
            """)
    void a_degree_the_profile_holds_under_another_name_is_met(String degree, String field,
                                                             String ask) {
        HardRequirements gate = gateFor(education(degree, field), NO_CERTIFICATIONS);

        assertThat(gate.unmet(posting(required(ask, RequirementKind.EDUCATION)))).isEmpty();
    }

    @Test
    void certifications_alone_say_nothing_about_a_degree() {
        HardRequirements gate = gateFor(List.of(), List.of(new Certification(UUID.randomUUID(),
                USER, "PRINCE2", "Axelos", null, null, null, null)));

        assertThat(gate.unmet(posting(required("Du er uddannet cand.jur.",
                RequirementKind.EDUCATION)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            CISSP, CISM eller CISA                     | CISM
            PMP or PRINCE2                             | PRINCE2
            Lead Auditor eller Lead Implementer        | Lead Implementer
            """)
    void holding_any_of_the_named_alternatives_is_enough(String ask, String held) {
        HardRequirements gate = gateFor(education("Bachelor", "Datalogi"), List.of(
                new Certification(UUID.randomUUID(), USER, held, null, null, null, null, null)));

        assertThat(gate.unmet(posting(required(ask, RequirementKind.CERTIFICATION)))).isEmpty();
    }

    @Test
    void law_words_outside_the_education_record_are_not_a_law_degree() {
        Profile profile = new Profile(UUID.randomUUID(), USER, "Advokatsekretær",
                "Samarbejde med jurister, tidligere jurymedlem.", null, List.of(), List.of(),
                null, null, null, null, null, null, null);
        HardRequirements gate = HardRequirements.forProfile(profile,
                List.of(new ProfileSkill(UUID.randomUUID(), USER, "Juridisk korrektur", null,
                        null, null, false, 0, null)),
                education("Kandidat", "Kommunikation"), NO_CERTIFICATIONS);

        assertThat(gate.unmet(posting(required("Du er uddannet cand.jur.",
                RequirementKind.EDUCATION)))).contains("Kræver cand.jur.");
    }

    @Test
    void a_certification_the_profile_records_is_held() {
        HardRequirements gate = gateFor(List.of(), List.of(new Certification(UUID.randomUUID(),
                USER, "CISSP", "ISC2", null, null, null, null)));

        assertThat(gate.unmet(posting(required("Du har en CISSP-certificering",
                RequirementKind.CERTIFICATION)))).isEmpty();
    }

    @Test
    void a_profile_with_no_education_or_certifications_is_unknown_not_lacking() {
        HardRequirements gate = gateFor(List.of(), NO_CERTIFICATIONS);

        assertThat(gate.unmet(posting(required("Du er uddannet cand.jur.",
                RequirementKind.EDUCATION)))).isEmpty();
    }

    private static HardRequirements gateFor(List<Education> education,
                                            List<Certification> certifications) {
        Profile profile = new Profile(UUID.randomUUID(), USER, "Headline", "", null, List.of(),
                List.of(), null, null, null, null, null, null, null);
        return HardRequirements.forProfile(profile, List.of(), education, certifications);
    }

    private static List<Education> education(String degree, String field) {
        return List.of(new Education(UUID.randomUUID(), USER, null, degree, field, null, null,
                null, null, 0, null, null, List.of()));
    }

    private static JobRequirement required(String text, RequirementKind kind) {
        return new JobRequirement(text, RequirementTier.REQUIRED, kind, null);
    }

    private static Job posting(JobRequirement ask) {
        UUID id = UUID.randomUUID();
        return Job.builder().id(id).url("https://example.com/" + id).title("Stilling")
                .isActive(true).requirements(List.of(ask)).build();
    }
}
