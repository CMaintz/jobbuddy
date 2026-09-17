package com.autoapplicant.usecase.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.in.skills.GetSkillGapUseCase.SkillGapResult;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobSkillGapServiceTest {

    private final JobRepositoryPort jobs = mock(JobRepositoryPort.class);
    private final ProfileSkillRepositoryPort profileSkills = mock(ProfileSkillRepositoryPort.class);

    private final UUID jobId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private JobSkillGapService serviceWithTaxonomy(SkillTaxonomy... rows) {
        SkillTaxonomyRepositoryPort taxonomy = mock(SkillTaxonomyRepositoryPort.class);
        when(taxonomy.findAll()).thenReturn(List.of(rows));
        return new JobSkillGapService(jobs, profileSkills, new SkillCanonicalizer(taxonomy));
    }

    private void jobAsks(List<String> skills) {
        when(jobs.findById(jobId)).thenReturn(Optional.of(
                Job.builder().id(jobId).skills(skills).technologies(List.of()).build()));
    }

    private void userHolds(String... skillNames) {
        List<ProfileSkill> held = List.of(skillNames).stream()
                .map(n -> new ProfileSkill(UUID.randomUUID(), userId, n, null, null, null, false, 0, null))
                .toList();
        when(profileSkills.findByUserId(userId)).thenReturn(held);
    }

    @Test
    void a_held_skill_covers_a_requirement_written_as_its_alias() {
        JobSkillGapService service = serviceWithTaxonomy(
                new SkillTaxonomy(null, "Kubernetes", "kubernetes", null, "DevOps", List.of("k8s")));
        jobAsks(List.of("k8s"));
        userHolds("Kubernetes");

        SkillGapResult result = service.analyzeSkillGap(jobId, userId);

        // Reported with the posting's own wording, but counted as covered.
        assertThat(result.matched()).containsExactly("k8s");
        assertThat(result.missing()).isEmpty();
        assertThat(result.coveragePct()).isEqualTo(100);
    }

    @Test
    void a_genuinely_absent_skill_is_still_reported_missing() {
        JobSkillGapService service = serviceWithTaxonomy(
                new SkillTaxonomy(null, "Kubernetes", "kubernetes", null, "DevOps", List.of("k8s")));
        jobAsks(List.of("k8s", "Rust"));
        userHolds("Kubernetes");

        SkillGapResult result = service.analyzeSkillGap(jobId, userId);

        assertThat(result.matched()).containsExactly("k8s");
        assertThat(result.missing()).containsExactly("Rust");
        assertThat(result.coveragePct()).isEqualTo(50);
    }
}
