package com.autoapplicant.adapter.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import com.autoapplicant.domain.job.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobMapperTest {

    // ── toEntity → toDomain round trip ────────────────────────────────────────

    @Test
    void round_trip_preserves_every_field() {
        Job job = Job.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .source(JobSource.JOBINDEX)
                .sourceJobId("src-42")
                .url("https://example.com/job/2")
                .title("Developer")
                .companyId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .companyName("Corp")
                .descriptionRaw("<p>Raw description</p>")
                .descriptionClean("Clean description")
                .employmentType(EmploymentType.PART_TIME)
                .seniority(Seniority.SENIOR)
                .remoteType(RemoteType.REMOTE)
                .location("Aarhus C")
                .municipality("Aarhus")
                .region("Midtjylland")
                .country("DK")
                .salaryMin(45000)
                .salaryMax(55000)
                .currency("DKK")
                .technologies(List.of("Python", "Django"))
                .skills(List.of("Problem Solving"))
                .languages(List.of("Danish"))
                .postedAt(Instant.parse("2026-01-01T08:00:00Z"))
                .scrapedAt(Instant.parse("2026-01-02T08:00:00Z"))
                .aiSummary("AI summary")
                .aiTags(List.of("ml", "nlp"))
                .aiSeniorityEstimate("SENIOR_ESTIMATE")
                .duplicateGroupId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
                .isActive(false)
                .jobCategory(JobCategory.DATA_ANALYTICS)
                .shortDescription("Short teaser")
                .lastSeenAt(Instant.parse("2026-01-03T08:00:00Z"))
                .applicationDeadline(LocalDate.of(2026, 2, 1))
                .contact(new JobContact("Mette Hansen", "afdelingsleder", "mh@example.dk", "12345678"))
                .requiredSkills(List.of("Python"))
                .preferredSkills(List.of("Django"))
                .requirements(List.of(
                        new JobRequirement("Mindst 5 års erfaring med Python",
                                RequirementTier.REQUIRED, RequirementKind.EXPERIENCE, null),
                        new JobRequirement("Django", RequirementTier.PREFERRED,
                                RequirementKind.SKILL, "Django")))
                .build();

        Job roundTripped = JobMapper.toDomain(JobMapper.toEntity(job));

        // createdAt/updatedAt are stamped by the entity's @PrePersist, never copied from the domain.
        assertThat(roundTripped)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(job);
    }

    @Test
    void null_lists_in_domain_produce_empty_arrays_in_entity() {
        Job job = new Job(UUID.randomUUID(), null, null, "https://example.com/job/3",
                "Analyst", null, "Org", null, null,
                null, null, null, null, null, null, null,
                null, null, null,
                null, null, null,
                null, Instant.now(), null, null, null, null, false, null, Instant.now(), Instant.now(),
                null, null, null, null,
                null, null, null);

        JobEntity entity = JobMapper.toEntity(job);

        assertThat(entity.getTechnologies()).isEmpty();
        assertThat(entity.getSkills()).isEmpty();
        assertThat(entity.getLanguages()).isEmpty();
        assertThat(entity.getAiTags()).isEmpty();
        assertThat(entity.getRequiredSkills()).isEmpty();
        assertThat(entity.getPreferredSkills()).isEmpty();
        assertThat(entity.getRequirements()).isEmpty();
    }

    // ── toDomain tolerance of what is already in the database ────────────────

    @Test
    void null_string_arrays_map_to_empty_lists() {
        JobEntity e = minimalEntity();
        e.setTechnologies(null);
        e.setSkills(null);
        e.setLanguages(null);
        e.setAiTags(null);
        e.setRequiredSkills(null);
        e.setPreferredSkills(null);

        Job job = JobMapper.toDomain(e);

        assertThat(job.technologies()).isEmpty();
        assertThat(job.skills()).isEmpty();
        assertThat(job.languages()).isEmpty();
        assertThat(job.aiTags()).isEmpty();
        assertThat(job.requiredSkills()).isEmpty();
        assertThat(job.preferredSkills()).isEmpty();
    }

    @Test
    void unknown_enum_values_map_to_null() {
        JobEntity e = minimalEntity();
        e.setEmploymentType("NONEXISTENT_TYPE");
        e.setSeniority("ULTRA_SENIOR");
        e.setRemoteType("EVERYWHERE");
        e.setJobCategory("BASKET_WEAVING");

        Job job = JobMapper.toDomain(e);

        assertThat(job.employmentType()).isNull();
        assertThat(job.seniority()).isNull();
        assertThat(job.remoteType()).isNull();
        assertThat(job.jobCategory()).isNull();
    }

    @Test
    void null_source_maps_to_null() {
        JobEntity e = minimalEntity();
        e.setSource(null);

        Job job = JobMapper.toDomain(e);

        assertThat(job.source()).isNull();
    }

    @Test
    void malformed_requirement_rows_are_skipped_and_unknown_tiers_default() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(null);
        rows.add(row(null, "REQUIRED", "SKILL"));
        rows.add(row("   ", "REQUIRED", "SKILL"));
        Map<String, Object> nonStringText = new HashMap<>();
        nonStringText.put("text", 42);
        rows.add(nonStringText);
        rows.add(row("Kørekort B", "MANDATORY", "VEHICLE"));
        JobEntity e = minimalEntity();
        e.setRequirements(rows);

        Job job = JobMapper.toDomain(e);

        // An untierable ask is a preference and an unknown kind is OTHER: never invent a demand.
        assertThat(job.requirements()).containsExactly(
                new JobRequirement("Kørekort B", RequirementTier.PREFERRED, RequirementKind.OTHER, null));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> row(String text, String tier, String kind) {
        Map<String, Object> row = new HashMap<>();
        row.put("text", text);
        row.put("tier", tier);
        row.put("kind", kind);
        return row;
    }

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
