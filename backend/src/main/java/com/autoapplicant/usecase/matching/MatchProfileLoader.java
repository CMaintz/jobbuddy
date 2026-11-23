package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Loads what a match score reads about one user, once per feed: their skills, the suggested
 * skills they have not answered yet, and the education, certifications and work history that
 * decide whether a posting's hard requirements are within reach.
 */
@Component
public class MatchProfileLoader {

    /**
     * The scorers for one profile, plus the skill rows its search text is built from. Suggested
     * skills are kept apart from {@code skills}: they are not the user's account of themselves.
     */
    record MatchProfile(Profile profile, List<ProfileSkill> skills, List<String> suggested,
                        SkillFitScorer skillFit, HardRequirements hardRequirements,
                        RequiredExperience requiredExperience) {}

    /** The repositories a match profile is read from. */
    @Component
    record Sources(ProfileSkillRepositoryPort skills, ParsedSkillSuggestionRepositoryPort suggested,
                   EducationRepositoryPort education, CertificationRepositoryPort certifications,
                   WorkExperienceRepositoryPort workExperience) {}

    private final SkillCanonicalizer canonicalizer;
    private final Sources sources;
    private final SkillSimilaritySource similarity;
    private final Clock clock;

    MatchProfileLoader(SkillCanonicalizer canonicalizer, Sources sources,
                       SkillSimilaritySource similarity, Clock clock) {
        this.canonicalizer = canonicalizer;
        this.sources = sources;
        this.similarity = similarity;
        this.clock = clock;
    }

    /** The profile scored on spelling alone, until {@link #againstPostings} knows the asks. */
    MatchProfile load(Profile profile) {
        List<ProfileSkill> skills = sources.skills().findByUserId(profile.userId());
        List<String> suggested = sources.suggested().findByUserId(profile.userId()).stream()
                .map(ParsedSkillSuggestion::skillName).filter(Objects::nonNull).toList();
        return new MatchProfile(profile, skills, suggested,
                SkillFitScorer.forProfile(canonicalizer, profile, skills, suggested,
                        SkillSimilarity.NONE),
                HardRequirements.forProfile(profile, skills,
                        sources.education().findByUserId(profile.userId()),
                        sources.certifications().findByUserId(profile.userId())),
                RequiredExperience.forHistory(
                        sources.workExperience().findByUserId(profile.userId()),
                        LocalDate.now(clock)));
    }

    /**
     * The same profile, now also crediting asks close in meaning to its skills. Done once for
     * the whole candidate list, so the labels nobody has embedded yet go out in one batch.
     */
    MatchProfile againstPostings(MatchProfile loaded, List<Job> jobs) {
        List<String> held = loaded.skills().stream().map(ProfileSkill::skillName)
                .filter(Objects::nonNull).toList();
        List<String> labels = new ArrayList<>(LooseCredit.comparedForms(held, canonicalizer));
        labels.addAll(LooseCredit.comparedForms(loaded.suggested(), canonicalizer));
        jobs.forEach(job -> asksOf(job).forEach(labels::add));
        // A lacked experience area costs less the closer the profile's skills come to it, which
        // only a label compared by meaning can tell.
        labels.addAll(RequiredExperience.areaLabels());
        SkillSimilarity known = similarity.among(labels.stream().filter(Objects::nonNull).toList());
        return new MatchProfile(loaded.profile(), loaded.skills(), loaded.suggested(),
                SkillFitScorer.forProfile(canonicalizer, loaded.profile(), loaded.skills(),
                        loaded.suggested(), known),
                loaded.hardRequirements(), loaded.requiredExperience());
    }

    private static Stream<String> asksOf(Job job) {
        return Stream.of(job.requiredSkills(), job.preferredSkills(), job.technologies(),
                        job.skills())
                .filter(Objects::nonNull).flatMap(List::stream);
    }
}
