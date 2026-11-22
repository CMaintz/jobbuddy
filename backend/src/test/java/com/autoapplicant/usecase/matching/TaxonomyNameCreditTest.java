package com.autoapplicant.usecase.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * A CV import stores a skill under its taxonomy's English master name. An ask close in meaning to
 * the Danish name the taxonomy also knows it by must earn the same credit.
 */
class TaxonomyNameCreditTest {

    private static final String ASK = "Pressemeddelelser";
    private static final String DANISH_NAME = "pressearbejde";

    private final UUID userId = UUID.randomUUID();
    private final Profile profile = new Profile(UUID.randomUUID(), userId, null, null, null,
            List.of(), List.of(), null, null, null, null, null, null, null);

    @Test
    void an_ask_close_to_the_danish_name_of_an_english_held_skill_earns_credit() {
        MatchProfileLoader loader = loaderFor("Public Relations");
        MatchProfileLoader.MatchProfile loaded = loader.load(profile);
        Job close = required(ASK);
        Job unrelated = required("Bogholderi");

        SkillFitScorer scorer = loader.againstPostings(loaded, List.of(close, unrelated)).skillFit();

        assertThat(scorer.score(close, new ArrayList<>()))
                .isGreaterThan(scorer.score(unrelated, new ArrayList<>()));
    }

    private MatchProfileLoader loaderFor(String held) {
        SkillTaxonomyRepositoryPort taxonomy = mock(SkillTaxonomyRepositoryPort.class);
        when(taxonomy.findAll()).thenReturn(List.of(new SkillTaxonomy(UUID.randomUUID(),
                "Public Relations", "public relations", null, "Domain", List.of(DANISH_NAME))));
        ProfileSkillRepositoryPort skills = mock(ProfileSkillRepositoryPort.class);
        when(skills.findByUserId(userId)).thenReturn(List.of(new ProfileSkill(UUID.randomUUID(),
                userId, held, null, null, null, false, 0, null)));
        return new MatchProfileLoader(new SkillCanonicalizer(taxonomy),
                new MatchProfileLoader.Sources(skills,
                        mock(ParsedSkillSuggestionRepositoryPort.class),
                        mock(EducationRepositoryPort.class), mock(CertificationRepositoryPort.class)),
                TaxonomyNameCreditTest::embeddedOnly);
    }

    /** Like the real embeddings: a label nobody handed over for embedding has no vector. */
    private static SkillSimilarity embeddedOnly(Collection<String> labels) {
        Set<String> embedded = labels.stream().map(SkillNames::normalize)
                .collect(Collectors.toSet());
        return (ask, skill) -> embedded.contains(SkillNames.normalize(ask))
                && embedded.contains(SkillNames.normalize(skill))
                && SkillNames.normalize(ask).equals(SkillNames.normalize(ASK))
                && SkillNames.normalize(skill).equals(DANISH_NAME) ? 0.9 : 0;
    }

    private static Job required(String... asks) {
        return Job.builder().id(UUID.randomUUID()).title("Opslag")
                .requiredSkills(List.of(asks)).build();
    }
}
