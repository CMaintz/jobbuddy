package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.EvidenceGap;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.InterviewStory;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.usecase.job.MarketCorpusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

class EvidenceGapServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final ProfileSkillRepositoryPort profileSkillRepo = Mockito.mock(ProfileSkillRepositoryPort.class);
    private final ProfileRepositoryPort profileRepo = Mockito.mock(ProfileRepositoryPort.class);
    private final InterviewStoryRepositoryPort storyRepo = Mockito.mock(InterviewStoryRepositoryPort.class);
    private final MarketCorpusService marketCorpus = Mockito.mock(MarketCorpusService.class);

    private final EvidenceGapService service =
            new EvidenceGapService(profileSkillRepo, profileRepo, storyRepo, marketCorpus);

    @BeforeEach
    void setUp() {
        when(profileSkillRepo.findByUserId(USER)).thenReturn(List.of());
        when(profileRepo.findByUserId(USER)).thenReturn(Optional.empty());
        when(storyRepo.findByUserId(USER)).thenReturn(List.of());
        when(marketCorpus.collect(any(), anyBoolean())).thenReturn(List.of());
    }

    private void claims(String... skills) {
        List<ProfileSkill> profileSkills = java.util.Arrays.stream(skills)
                .map(name -> new ProfileSkill(UUID.randomUUID(), USER, name, null, null, null,
                        false, 0, null))
                .toList();
        when(profileSkillRepo.findByUserId(USER)).thenReturn(profileSkills);
    }

    private void marketAsksFor(List<String>... postings) {
        when(marketCorpus.collect(any(), anyBoolean())).thenReturn(
                java.util.Arrays.stream(postings)
                        .map(t -> Job.builder().id(UUID.randomUUID()).title("Role").technologies(t).build())
                        .toList());
    }

    private void storyTagged(String... tags) {
        when(storyRepo.findByUserId(USER)).thenReturn(List.of(new InterviewStory(
                UUID.randomUUID(), USER, "A story", null, null, "did the thing", "it worked",
                null, List.of(tags), null, null)));
    }

    @Test
    void aClaimedInDemandSkillWithNoStoryIsAGap() {
        claims("Kubernetes");
        marketAsksFor(List.of("Kubernetes"), List.of("Kubernetes"));

        assertThat(service.evidenceGaps(USER, 5)).singleElement().satisfies(gap -> {
            assertThat(gap.skillName()).isEqualTo("Kubernetes");
            assertThat(gap.marketFrequency()).isEqualTo(2);
            assertThat(gap.question()).contains("Kubernetes");
        });
    }

    @Test
    void aSkillWithAStoryIsAlreadyProven() {
        claims("Kubernetes");
        marketAsksFor(List.of("Kubernetes"));
        storyTagged("Kubernetes");

        assertThat(service.evidenceGaps(USER, 5)).isEmpty();
    }

    @Test
    void aStoryWrittenBeforeTaggingStillCounts() {
        claims("Kubernetes");
        marketAsksFor(List.of("Kubernetes"));
        when(storyRepo.findByUserId(USER)).thenReturn(List.of(new InterviewStory(
                UUID.randomUUID(), USER, "Migration", "We moved the platform to Kubernetes",
                null, "did it", "faster deploys", null, List.of(), null, null)));

        assertThat(service.evidenceGaps(USER, 5)).isEmpty();
    }

    @Test
    void aSkillNobodyIsHiringForIsNotWorthAsking() {
        claims("COBOL");
        marketAsksFor(List.of("Kubernetes"));
        assertThat(service.evidenceGaps(USER, 5)).isEmpty();
    }

    @Test
    void weNeverAskForEvidenceOfSomethingTheUserDidNotClaim() {
        // Kubernetes is in demand but not on the profile: that is a skill gap, not an evidence gap.
        marketAsksFor(List.of("Kubernetes"));
        assertThat(service.evidenceGaps(USER, 5)).isEmpty();
    }

    @Test
    void theMostDemandedGapComesFirst() {
        claims("Kubernetes", "Terraform");
        marketAsksFor(List.of("Kubernetes", "Terraform"), List.of("Terraform"), List.of("Terraform"));

        assertThat(service.evidenceGaps(USER, 5)).extracting(EvidenceGap::skillName)
                .containsExactly("Terraform", "Kubernetes");
    }

    @Test
    void legacyProfileListsCountAsClaims() {
        when(profileRepo.findByUserId(USER)).thenReturn(Optional.of(new Profile(
                UUID.randomUUID(), USER, null, null, null, List.of(), List.of("Kubernetes"),
                List.of(), List.of(), null, null, null, null, null, null, null)));
        marketAsksFor(List.of("Kubernetes"));

        assertThat(service.evidenceGaps(USER, 5)).extracting(EvidenceGap::skillName)
                .containsExactly("Kubernetes");
    }

    @Test
    void recordedEvidenceIsTaggedWithItsSkillSoTheGapCloses() {
        when(storyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        InterviewStory story = service.recordEvidence(USER, "Kubernetes",
                "Platform team at Acme", "Moved 30 services onto it", "Deploys went from 40 min to 9");

        assertThat(story.tags()).containsExactly("Kubernetes");
        assertThat(story.title()).isEqualTo("Kubernetes");
        assertThat(story.action()).isEqualTo("Moved 30 services onto it");
    }

    @Test
    void contextWithoutSubstanceIsNotEvidence() {
        // A situation alone proves nothing a letter could cite.
        assertThatThrownBy(() -> service.recordEvidence(USER, "Kubernetes", "At Acme", "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordEvidence(USER, "  ", null, "did it", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
