package com.autoapplicant.usecase.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.skill.SkillCandidate;
import com.autoapplicant.domain.skill.SkillConfirmation;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillCandidateInferenceTest extends SkillCandidateServiceFixture {

    // The parsers stay extractive: what they write to the profile is what the fact guard treats
    // as the candidate's own account. A skill their CV evidences without naming arrives here
    // instead, as a question, so a wrong reading costs a dismissal rather than a fabricated skill.

    @Test
    void aSkillTheUsersOwnCvEvidencesIsOfferedWithTheLineThatImpliedIt() {
        queueInferred("Scrum", "Ran fortnightly retrospectives and groomed the backlog");

        List<SkillCandidate> candidates = service.suggest(USER, 10);

        assertThat(candidates).extracting(SkillCandidate::name).contains("Scrum");
        SkillCandidate scrum = candidates.stream().filter(c -> c.name().equals("Scrum")).findFirst().orElseThrow();
        assertThat(scrum.source()).isEqualTo(SkillCandidate.SkillCandidateSource.DOCUMENT_INFERRED);
        assertThat(scrum.evidence()).isEqualTo("Ran fortnightly retrospectives and groomed the backlog");
    }

    @Test
    void aDocumentInferenceOutranksSomethingMerelyInDemand() {
        queueInferred("Scrum", "Ran fortnightly retrospectives");
        market(posting(List.of("Kubernetes"), List.of()),
               posting(List.of("Kubernetes"), List.of()),
               posting(List.of("Kubernetes"), List.of()));

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).startsWith("Scrum");
    }

    @Test
    void aDocumentInferenceSurvivesTheNoDemandCap() {
        // The inference has no market demand either, but it carries its own explanation, so it
        // must not use up one of the five no-demand slots the neighbours compete for.
        marketlessNeighbours(7);
        queueInferred("Scrum", "Ran fortnightly retrospectives");

        List<SkillCandidate> candidates = service.suggest(USER, 20);

        assertThat(candidates).extracting(SkillCandidate::name).contains("Scrum");
        assertThat(candidates).hasSize(6);
    }

    @Test
    void anInferenceForSomethingTheUserAlreadyClaimsIsNotOffered() {
        profileWith("Scrum");
        queueInferred("Scrum", "Ran fortnightly retrospectives");

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).doesNotContain("Scrum");
    }

    @Test
    void answeringAnInferenceTakesItOutOfTheQueueWhicheverWayItIsAnswered() {
        queueInferred("Scrum", "Ran fortnightly retrospectives");
        when(profileSkillRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.confirm(USER, List.of(new SkillConfirmation("Scrum",
                SkillConfirmation.Decision.YES, true, null)));
        assertThat(parsedSuggestions).isEmpty();

        queueInferred("Kanban", "Managed a WIP-limited board");
        service.confirm(USER, List.of(new SkillConfirmation("Kanban",
                SkillConfirmation.Decision.NO, false, null)));
        assertThat(parsedSuggestions).isEmpty();
        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).doesNotContain("Kanban");
    }

    @Test
    void aSkippedInferenceStaysQueuedForNextTime() {
        queueInferred("Scrum", "Ran fortnightly retrospectives");

        service.confirm(USER, List.of(new SkillConfirmation("Scrum",
                SkillConfirmation.Decision.SKIP, false, null)));

        assertThat(service.suggest(USER, 10)).extracting(SkillCandidate::name).contains("Scrum");
    }
}
