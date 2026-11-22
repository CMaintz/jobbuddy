package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.EducationRepositoryPort;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Loads what a match score reads about one user, once per feed: their skills, and the education
 * and certifications that decide whether a posting's hard requirements are within reach.
 */
@Component
public class MatchProfileLoader {

    /** The scorers for one profile, plus the skill rows its search text is built from. */
    record MatchProfile(List<ProfileSkill> skills, SkillFitScorer skillFit,
                        HardRequirements hardRequirements) {}

    private final SkillCanonicalizer canonicalizer;
    private final ProfileSkillRepositoryPort skillRepo;
    private final EducationRepositoryPort educationRepo;
    private final CertificationRepositoryPort certificationRepo;

    public MatchProfileLoader(SkillCanonicalizer canonicalizer,
                              ProfileSkillRepositoryPort skillRepo,
                              EducationRepositoryPort educationRepo,
                              CertificationRepositoryPort certificationRepo) {
        this.canonicalizer = canonicalizer;
        this.skillRepo = skillRepo;
        this.educationRepo = educationRepo;
        this.certificationRepo = certificationRepo;
    }

    MatchProfile load(Profile profile) {
        List<ProfileSkill> skills = skillRepo.findByUserId(profile.userId());
        return new MatchProfile(skills,
                SkillFitScorer.forProfile(canonicalizer, profile, skills),
                HardRequirements.forProfile(profile, skills,
                        educationRepo.findByUserId(profile.userId()),
                        certificationRepo.findByUserId(profile.userId())));
    }
}
