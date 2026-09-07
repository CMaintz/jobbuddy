package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.*;
import com.autoapplicant.domain.user.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileSectionMapperTest {

    // ── WorkExperience ────────────────────────────────────────────────────────

    @Test
    void work_experience_null_arrays_map_to_empty_list() {
        WorkExperienceEntity e = workExperienceEntity(null, null);

        WorkExperience d = ProfileSectionMapper.toDomain(e);

        assertThat(d.technologies()).isEmpty();
        assertThat(d.achievements()).isEmpty();
    }

    @Test
    void work_experience_arrays_map_to_lists() {
        WorkExperienceEntity e = workExperienceEntity(
                new String[]{"Java", "Spring"}, new String[]{"Led team"});

        WorkExperience d = ProfileSectionMapper.toDomain(e);

        assertThat(d.technologies()).containsExactly("Java", "Spring");
        assertThat(d.achievements()).containsExactly("Led team");
    }

    @Test
    void work_experience_toEntity_round_trip() {
        WorkExperienceEntity e = workExperienceEntity(new String[]{"Kotlin"}, new String[]{});
        WorkExperience domain = ProfileSectionMapper.toDomain(e);

        WorkExperienceEntity back = ProfileSectionMapper.toEntity(domain);

        assertThat(back.getTechnologies()).containsExactly("Kotlin");
        assertThat(back.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(back.getTitle()).isEqualTo("Engineer");
    }

    // ── Project ───────────────────────────────────────────────────────────────

    @Test
    void project_null_technologies_map_to_empty_list() {
        ProjectEntity e = projectEntity(null);

        Project d = ProfileSectionMapper.toDomain(e);

        assertThat(d.technologies()).isEmpty();
    }

    @Test
    void project_technologies_map_to_list() {
        ProjectEntity e = projectEntity(new String[]{"React", "Node"});

        Project d = ProfileSectionMapper.toDomain(e);

        assertThat(d.technologies()).containsExactly("React", "Node");
    }

    @Test
    void project_toEntity_preserves_fields() {
        ProjectEntity e = projectEntity(new String[]{"Go"});
        Project domain = ProfileSectionMapper.toDomain(e);

        ProjectEntity back = ProfileSectionMapper.toEntity(domain);

        assertThat(back.getName()).isEqualTo("My Project");
        assertThat(back.getTechnologies()).containsExactly("Go");
    }

    // ── Education ─────────────────────────────────────────────────────────────

    @Test
    void education_toDomain_maps_all_fields() {
        EducationEntity e = educationEntity();

        Education d = ProfileSectionMapper.toDomain(e);

        assertThat(d.institution()).isEqualTo("DTU");
        assertThat(d.degree()).isEqualTo("BSc");
        assertThat(d.fieldOfStudy()).isEqualTo("Computer Science");
        assertThat(d.skills()).isEmpty();
    }

    @Test
    void education_toEntity_round_trip() {
        EducationEntity e = educationEntity();
        Education domain = ProfileSectionMapper.toDomain(e);

        EducationEntity back = ProfileSectionMapper.toEntity(domain);

        assertThat(back.getInstitution()).isEqualTo("DTU");
        assertThat(back.getDegree()).isEqualTo("BSc");
    }

    // ── Certification ─────────────────────────────────────────────────────────

    @Test
    void certification_toDomain_maps_all_fields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CertificationEntity e = new CertificationEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setName("AWS Solutions Architect");
        e.setIssuer("Amazon");
        e.setIssuedAt(LocalDate.of(2023, 1, 1));
        e.setCredentialUrl("https://example.com");

        Certification d = ProfileSectionMapper.toDomain(e);

        assertThat(d.id()).isEqualTo(id);
        assertThat(d.userId()).isEqualTo(userId);
        assertThat(d.name()).isEqualTo("AWS Solutions Architect");
        assertThat(d.issuer()).isEqualTo("Amazon");
        assertThat(d.credentialUrl()).isEqualTo("https://example.com");
    }

    @Test
    void certification_toEntity_round_trip() {
        CertificationEntity e = new CertificationEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(UUID.randomUUID());
        e.setName("GCP Associate");
        e.setIssuer("Google");
        Certification domain = ProfileSectionMapper.toDomain(e);

        CertificationEntity back = ProfileSectionMapper.toEntity(domain);

        assertThat(back.getName()).isEqualTo("GCP Associate");
        assertThat(back.getIssuer()).isEqualTo("Google");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WorkExperienceEntity workExperienceEntity(String[] technologies, String[] achievements) {
        WorkExperienceEntity e = new WorkExperienceEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(UUID.randomUUID());
        e.setCompanyName("Acme Corp");
        e.setTitle("Engineer");
        e.setStartDate(LocalDate.of(2020, 1, 1));
        e.setCurrent(false);
        e.setDisplayOrder(0);
        e.setTechnologies(technologies);
        e.setAchievements(achievements);
        return e;
    }

    private ProjectEntity projectEntity(String[] technologies) {
        ProjectEntity e = new ProjectEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(UUID.randomUUID());
        e.setName("My Project");
        e.setFeatured(false);
        e.setDisplayOrder(0);
        e.setTechnologies(technologies);
        return e;
    }

    private EducationEntity educationEntity() {
        EducationEntity e = new EducationEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(UUID.randomUUID());
        e.setInstitution("DTU");
        e.setDegree("BSc");
        e.setFieldOfStudy("Computer Science");
        e.setStartDate(LocalDate.of(2015, 9, 1));
        e.setDisplayOrder(0);
        return e;
    }
}
