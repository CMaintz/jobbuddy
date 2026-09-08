package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.in.skills.ManageProfileSkillsUseCase;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileSkillService implements ManageProfileSkillsUseCase {

    private final ProfileSkillRepositoryPort repo;
    private final SkillTaxonomyRepositoryPort taxonomyRepo;

    public ProfileSkillService(ProfileSkillRepositoryPort repo,
                               SkillTaxonomyRepositoryPort taxonomyRepo) {
        this.repo = repo;
        this.taxonomyRepo = taxonomyRepo;
    }

    @Override
    public List<ProfileSkill> getSkills(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public ProfileSkill addSkill(ProfileSkill skill) {
        return repo.save(resolve(skill));
    }

    @Override
    public ProfileSkill updateSkill(ProfileSkill skill) {
        return repo.save(resolve(skill));
    }

    @Override
    public void deleteSkill(UUID skillId, UUID userId) {
        repo.findById(skillId)
                .filter(s -> s.userId().equals(userId))
                .ifPresent(s -> repo.deleteById(skillId));
    }

    /**
     * Links the skill to its taxonomy entry and fills in its category.
     *
     * <p>Without this, a skill typed rather than picked from the autocomplete arrived with no
     * taxonomy id, and category was derived only from a taxonomy id — so it stayed uncategorised
     * for good. Matching on the normalized name means typing "kubernetes" lands on the same
     * taxonomy row as picking Kubernetes from the list.
     *
     * <p>A category the caller supplied is kept as-is: the taxonomy fills gaps, it does not
     * overrule someone who filed the skill deliberately. A skill the taxonomy has never heard
     * of keeps whatever category it came with, including none.
     */
    private ProfileSkill resolve(ProfileSkill skill) {
        if (skill.skillName() == null || skill.skillName().isBlank()) return skill;
        if (skill.taxonomyId() != null && skill.category() != null) return skill;

        Optional<SkillTaxonomy> match =
                taxonomyRepo.findByNormalizedName(normalize(skill.skillName()));
        if (match.isEmpty()) return skill;

        SkillTaxonomy t = match.get();
        return new ProfileSkill(
                skill.id(), skill.userId(), skill.skillName(),
                skill.taxonomyId() != null ? skill.taxonomyId() : t.id(),
                skill.proficiencyLevel(), skill.yearsExperience(), skill.usedInProduction(),
                skill.displayOrder(),
                skill.category() != null ? skill.category() : t.category());
    }

    private static String normalize(String name) {
        return SkillNames.normalize(name);
    }
}
