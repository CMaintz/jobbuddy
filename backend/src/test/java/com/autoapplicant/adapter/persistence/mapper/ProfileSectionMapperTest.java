package com.autoapplicant.adapter.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.entity.*;
import com.autoapplicant.domain.user.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ProfileSectionMapperTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    // Each round trip sets every persisted field to a distinct value, so a setter dropped from
    // toEntity or a getter swapped in toDomain shows up as a field mismatch. createdAt/updatedAt
    // are stamped by the entity lifecycle callbacks, never copied from the domain.

    @Test
    void work_experience_round_trip_preserves_every_field() {
        WorkExperience original = new WorkExperience(ID, USER_ID, "Acme Corp", "Engineer",
                "Copenhagen", "Built the billing platform",
                LocalDate.of(2020, 1, 1), LocalDate.of(2023, 6, 30), true,
                List.of("Java", "Spring"), List.of("Led team"), 3, null, null, List.of());

        WorkExperience roundTripped = ProfileSectionMapper.toDomain(ProfileSectionMapper.toEntity(original));

        assertThat(roundTripped).usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt").isEqualTo(original);
    }

    @Test
    void project_round_trip_preserves_every_field() {
        Project original = new Project(ID, USER_ID, "My Project", "A side project",
                List.of("React", "Node"), "https://github.com/me/proj", "https://proj.example.com",
                "Event-sourced core", "Cut p99 latency by 40%", "Saved 2 FTE",
                LocalDate.of(2021, 3, 1), LocalDate.of(2022, 4, 1), true, 5, null, null, List.of());

        Project roundTripped = ProfileSectionMapper.toDomain(ProfileSectionMapper.toEntity(original));

        assertThat(roundTripped).usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt").isEqualTo(original);
    }

    @Test
    void education_round_trip_preserves_every_field() {
        Education original = new Education(ID, USER_ID, "DTU", "BSc", "Computer Science",
                LocalDate.of(2015, 9, 1), LocalDate.of(2018, 6, 30), "Thesis on compilers",
                "12.0", 2, null, null, List.of());

        Education roundTripped = ProfileSectionMapper.toDomain(ProfileSectionMapper.toEntity(original));

        assertThat(roundTripped).usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt").isEqualTo(original);
    }

    @Test
    void certification_round_trip_preserves_every_field() {
        Certification original = new Certification(ID, USER_ID, "AWS Solutions Architect", "Amazon",
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), "https://example.com/cred", null);

        Certification roundTripped = ProfileSectionMapper.toDomain(ProfileSectionMapper.toEntity(original));

        assertThat(roundTripped).usingRecursiveComparison()
                .ignoringFields("createdAt").isEqualTo(original);
    }

    // Rows written before a column existed come back with null arrays; callers iterate these
    // lists without null checks.
    static Stream<Named<Supplier<List<String>>>> nullArrayColumns() {
        return Stream.of(
                Named.of("work experience technologies", () -> {
                    WorkExperienceEntity e = workExperienceEntity();
                    e.setTechnologies(null);
                    return ProfileSectionMapper.toDomain(e).technologies();
                }),
                Named.of("work experience achievements", () -> {
                    WorkExperienceEntity e = workExperienceEntity();
                    e.setAchievements(null);
                    return ProfileSectionMapper.toDomain(e).achievements();
                }),
                Named.of("project technologies", () -> {
                    ProjectEntity e = projectEntity();
                    e.setTechnologies(null);
                    return ProfileSectionMapper.toDomain(e).technologies();
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullArrayColumns")
    void null_array_column_maps_to_empty_list(Supplier<List<String>> mappedList) {
        assertThat(mappedList.get()).isNotNull().isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static WorkExperienceEntity workExperienceEntity() {
        WorkExperienceEntity e = new WorkExperienceEntity();
        e.setId(ID);
        e.setUserId(USER_ID);
        e.setCompanyName("Acme Corp");
        e.setTitle("Engineer");
        e.setStartDate(LocalDate.of(2020, 1, 1));
        e.setTechnologies(new String[]{"Java"});
        e.setAchievements(new String[]{"Led team"});
        return e;
    }

    private static ProjectEntity projectEntity() {
        ProjectEntity e = new ProjectEntity();
        e.setId(ID);
        e.setUserId(USER_ID);
        e.setName("My Project");
        e.setTechnologies(new String[]{"Go"});
        return e;
    }
}
