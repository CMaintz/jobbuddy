package com.autoapplicant.usecase.skills;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillCandidateDismissalRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.usecase.job.MarketCorpusService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

/** Shared set-up for the SkillCandidateService suites: mocked market and taxonomy, real queues. */
abstract class SkillCandidateServiceFixture {

    static final UUID USER = UUID.randomUUID();

    final ProfileSkillRepositoryPort profileSkillRepo = Mockito.mock(ProfileSkillRepositoryPort.class);
    final SkillTaxonomyRepositoryPort taxonomyRepo = Mockito.mock(SkillTaxonomyRepositoryPort.class);
    final MarketCorpusService marketCorpus = Mockito.mock(MarketCorpusService.class);

    /** Real dismissal store: "never suggest this again" is behaviour worth testing end to end. */
    final Set<String> dismissed = new HashSet<>();
    final SkillCandidateDismissalRepositoryPort dismissalRepo =
            new SkillCandidateDismissalRepositoryPort() {
                @Override public Set<String> findDismissedNames(UUID userId) { return dismissed; }
                @Override public void dismiss(UUID userId, String normalizedName) { dismissed.add(normalizedName); }
            };

    /** Real suggestion queue too: what a confirmed or declined suggestion does to it is behaviour. */
    final List<ParsedSkillSuggestion> parsedSuggestions = new ArrayList<>();
    final ParsedSkillSuggestionRepositoryPort parsedSuggestionRepo =
            new ParsedSkillSuggestionRepositoryPort() {
                @Override public List<ParsedSkillSuggestion> findByUserId(UUID userId) {
                    return List.copyOf(parsedSuggestions);
                }
                @Override public void record(ParsedSkillSuggestion s) {
                    if (parsedSuggestions.stream().noneMatch(e -> e.normalizedName().equals(s.normalizedName()))) {
                        parsedSuggestions.add(s);
                    }
                }
                @Override public void remove(UUID userId, String normalizedName) {
                    parsedSuggestions.removeIf(s -> s.normalizedName().equals(normalizedName));
                }
            };

    final SkillCandidateService service = new SkillCandidateService(
            profileSkillRepo, taxonomyRepo, dismissalRepo, marketCorpus, parsedSuggestionRepo,
            new SkillResolver(taxonomyRepo, new SkillCanonicalizer(taxonomyRepo)));

    @BeforeEach
    void setUp() {
        when(profileSkillRepo.findByUserId(USER)).thenReturn(List.of());
        when(taxonomyRepo.findByNormalizedName(any())).thenReturn(Optional.empty());
        when(taxonomyRepo.findByParentIds(any())).thenReturn(List.of());
        when(marketCorpus.collect(any(), anyBoolean())).thenReturn(List.of());
    }

    /** Gives the user these skills: one profile_skills row each, the only place skills live. */
    void profileWith(String... skills) {
        when(profileSkillRepo.findByUserId(USER)).thenReturn(
                java.util.Arrays.stream(skills)
                        .map(name -> new ProfileSkill(UUID.randomUUID(), USER, name, null,
                                null, null, false, 0, null))
                        .toList());
    }

    static Job posting(List<String> technologies, List<String> skills) {
        return Job.builder().id(UUID.randomUUID()).title("Role")
                .technologies(technologies).skills(skills).build();
    }

    void market(Job... jobs) {
        when(marketCorpus.collect(any(), anyBoolean())).thenReturn(List.of(jobs));
    }

    /** A Spring Boot profile whose taxonomy siblings no posting asks for. */
    void marketlessNeighbours(int count) {
        profileWith("Spring Boot");
        UUID parentId = UUID.randomUUID();
        when(taxonomyRepo.findByNormalizedName("spring boot")).thenReturn(Optional.of(
                new SkillTaxonomy(UUID.randomUUID(), "Spring Boot", "spring boot", parentId, "F", List.of())));
        List<SkillTaxonomy> neighbours = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            neighbours.add(new SkillTaxonomy(UUID.randomUUID(), "Neighbour " + i,
                    "neighbour " + i, parentId, "F", List.of()));
        }
        when(taxonomyRepo.findByParentIds(any())).thenReturn(neighbours);
    }

    void queueInferred(String name, String evidence) {
        parsedSuggestionRepo.record(new ParsedSkillSuggestion(UUID.randomUUID(), USER, name,
                name.toLowerCase(java.util.Locale.ROOT), evidence,
                ParsedSkillSuggestion.Source.CV_PARSE));
    }
}
