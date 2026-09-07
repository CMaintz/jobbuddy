package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import com.autoapplicant.domain.job.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobMapperTest {

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    void null_string_arrays_map_to_empty_lists() {
        JobEntity e = minimalEntity();
        e.setTechnologies(null);
        e.setSkills(null);
        e.setLanguages(null);
        e.setAiTags(null);

        Job job = JobMapper.toDomain(e);

        assertThat(job.technologies()).isEmpty();
        assertThat(job.skills()).isEmpty();
        assertThat(job.languages()).isEmpty();
        assertThat(job.aiTags()).isEmpty();
    }

    @Test
    void string_arrays_map_to_lists() {
        JobEntity e = minimalEntity();
        e.setTechnologies(new String[]{"Java", "Kotlin"});
        e.setSkills(new String[]{"Leadership"});
        e.setAiTags(new String[]{"backend", "senior"});

        Job job = JobMapper.toDomain(e);

        assertThat(job.technologies()).containsExactly("Java", "Kotlin");
        assertThat(job.skills()).containsExactly("Leadership");
        assertThat(job.aiTags()).containsExactly("backend", "senior");
    }

    @Test
    void unknown_enum_values_map_to_null() {
        JobEntity e = minimalEntity();
        e.setEmploymentType("NONEXISTENT_TYPE");
        e.setSeniority("ULTRA_SENIOR");
        e.setRemoteType("EVERYWHERE");

        Job job = JobMapper.toDomain(e);

        assertThat(job.employmentType()).isNull();
        assertThat(job.seniority()).isNull();
        assertThat(job.remoteType()).isNull();
    }

    @Test
    void valid_enum_values_map_correctly() {
        JobEntity e = minimalEntity();
        e.setEmploymentType("FULL_TIME");
        e.setSeniority("MID");
        e.setRemoteType("HYBRID");

        Job job = JobMapper.toDomain(e);

        assertThat(job.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(job.seniority()).isEqualTo(Seniority.MID);
        assertThat(job.remoteType()).isEqualTo(RemoteType.HYBRID);
    }

    @Test
    void null_source_maps_to_null() {
        JobEntity e = minimalEntity();
        e.setSource(null);

        Job job = JobMapper.toDomain(e);

        assertThat(job.source()).isNull();
    }

    @Test
    void toDomain_preserves_all_scalar_fields() {
        UUID id = UUID.randomUUID();
        JobEntity e = minimalEntity();
        e.setId(id);
        e.setTitle("Backend Engineer");
        e.setCompanyName("Acme");
        e.setUrl("https://example.com/job/1");
        e.setLocation("Copenhagen");
        e.setSalaryMin(60000);
        e.setSalaryMax(90000);
        e.setCurrency("DKK");
        e.setActive(true);

        Job job = JobMapper.toDomain(e);

        assertThat(job.id()).isEqualTo(id);
        assertThat(job.title()).isEqualTo("Backend Engineer");
        assertThat(job.companyName()).isEqualTo("Acme");
        assertThat(job.url()).isEqualTo("https://example.com/job/1");
        assertThat(job.location()).isEqualTo("Copenhagen");
        assertThat(job.salaryMin()).isEqualTo(60000);
        assertThat(job.salaryMax()).isEqualTo(90000);
        assertThat(job.currency()).isEqualTo("DKK");
        assertThat(job.isActive()).isTrue();
    }

    // ── toEntity → toDomain round trip ────────────────────────────────────────

    @Test
    void round_trip_preserves_lists_and_enums() {
        Job job = new Job(UUID.randomUUID(), JobSource.MANUAL, null,
                "https://example.com/job/2", "Developer", null, "Corp",
                null, "Clean description",
                EmploymentType.PART_TIME, Seniority.SENIOR, RemoteType.REMOTE,
                "Aarhus", null, null, "DK",
                null, null, "DKK",
                List.of("Python", "Django"), List.of("Problem Solving"), List.of("Danish"),
                Instant.now(), Instant.now(), "AI summary",
                List.of("ml", "nlp"), "SENIOR",
                null, true, null, Instant.now(), Instant.now(), null);

        Job roundTripped = JobMapper.toDomain(JobMapper.toEntity(job));

        assertThat(roundTripped.source()).isEqualTo(JobSource.MANUAL);
        assertThat(roundTripped.employmentType()).isEqualTo(EmploymentType.PART_TIME);
        assertThat(roundTripped.seniority()).isEqualTo(Seniority.SENIOR);
        assertThat(roundTripped.remoteType()).isEqualTo(RemoteType.REMOTE);
        assertThat(roundTripped.technologies()).containsExactly("Python", "Django");
        assertThat(roundTripped.skills()).containsExactly("Problem Solving");
        assertThat(roundTripped.languages()).containsExactly("Danish");
        assertThat(roundTripped.aiTags()).containsExactly("ml", "nlp");
        assertThat(roundTripped.isActive()).isTrue();
    }

    @Test
    void null_lists_in_domain_produce_empty_arrays_in_entity() {
        Job job = new Job(UUID.randomUUID(), null, null, "https://example.com/job/3",
                "Analyst", null, "Org", null, null,
                null, null, null, null, null, null, null,
                null, null, null,
                null, null, null,
                null, Instant.now(), null, null, null, null, false, null, Instant.now(), Instant.now(), null);

        JobEntity entity = JobMapper.toEntity(job);

        assertThat(entity.getTechnologies()).isEmpty();
        assertThat(entity.getSkills()).isEmpty();
        assertThat(entity.getAiTags()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JobEntity minimalEntity() {
        JobEntity e = new JobEntity();
        e.setId(UUID.randomUUID());
        e.setSource("LINKEDIN");
        e.setUrl("https://example.com/job/0");
        e.setTitle("Software Engineer");
        e.setScrapedAt(Instant.now());
        e.setActive(true);
        return e;
    }
}
