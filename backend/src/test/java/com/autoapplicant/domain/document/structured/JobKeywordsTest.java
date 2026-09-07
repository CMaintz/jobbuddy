package com.autoapplicant.domain.document.structured;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobKeywordsTest {

    private static JobRequirement req(String text, RequirementKind kind) {
        return new JobRequirement(text, RequirementTier.REQUIRED, kind, null);
    }

    @Test
    void skillAsksAreLeftOutOfTheUnmeasurableList() {
        JobKeywords keywords = new JobKeywords(List.of("Java"), List.of(), List.of(
                req("Erfaring med Java", RequirementKind.SKILL),
                req("5 års erfaring med backend", RequirementKind.EXPERIENCE),
                req("Kørekort B", RequirementKind.OTHER)));

        // A skill ask is already counted by the keyword check; listing it again would read as
        // two findings about one requirement.
        assertThat(keywords.unmeasurableRequirements())
                .extracting(JobRequirement::text)
                .containsExactly("5 års erfaring med backend", "Kørekort B");
    }

    @Test
    void anEnrichedJobCarriesItsRequirementsThrough() {
        Job job = Job.builder().id(UUID.randomUUID()).title("Backend-udvikler")
                .requiredSkills(List.of("Java"))
                .requirements(List.of(req("Kandidatgrad i datalogi", RequirementKind.EDUCATION)))
                .build();

        JobKeywords keywords = JobKeywords.of(job);
        assertThat(keywords.required()).containsExactly("Java");
        assertThat(keywords.unmeasurableRequirements()).hasSize(1);
    }

    @Test
    void anUnenrichedJobHasNothingToMeasureOrList() {
        assertThat(JobKeywords.of(null)).isEqualTo(JobKeywords.NONE);
        assertThat(JobKeywords.NONE.isEmpty()).isTrue();
        assertThat(JobKeywords.NONE.unmeasurableRequirements()).isEmpty();
    }
}
