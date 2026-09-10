package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.in.skills.ManageProfileSkillsUseCase;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileSkillService implements ManageProfileSkillsUseCase {

    private final ProfileSkillRepositoryPort repo;
    private final SkillResolver resolver;

    public ProfileSkillService(ProfileSkillRepositoryPort repo, SkillResolver resolver) {
        this.repo = repo;
        this.resolver = resolver;
    }

    @Override
    public List<ProfileSkill> getSkills(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public ProfileSkill addSkill(ProfileSkill skill) {
        ProfileSkill resolved = resolve(skill);
        // Collapsing an alias onto its master ("k8s" -> Kubernetes) can land on a skill the user
        // already holds under the canonical name, which unique_user_skill forbids. Merge into that
        // row instead of inserting a duplicate the DB would reject.
        if (resolved.id() == null) {
            Optional<ProfileSkill> existing = repo.findByUserId(resolved.userId()).stream()
                    .filter(e -> resolver.key(e.skillName()).equals(resolver.key(resolved.skillName())))
                    .findFirst();
            if (existing.isPresent()) return repo.save(mergeInto(existing.get(), resolved));
        }
        return repo.save(resolved);
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
     * Links the skill to its taxonomy entry, fills in its category, and collapses its name onto the
     * taxonomy master.
     *
     * <p>Without this, a skill typed rather than picked from the autocomplete arrived with no
     * taxonomy id, and category was derived only from a taxonomy id — so it stayed uncategorised
     * for good. Resolving through {@link SkillResolver} means typing "kubernetes" — or "k8s" — lands
     * on the same Kubernetes row as picking it from the list, and is stored under that one spelling.
     *
     * <p>A category the caller supplied is kept as-is: the taxonomy fills gaps, it does not overrule
     * someone who filed the skill deliberately. A skill the taxonomy has never heard of keeps
     * whatever name and category it came with, including none.
     */
    private ProfileSkill resolve(ProfileSkill skill) {
        if (skill.skillName() == null || skill.skillName().isBlank()) return skill;
        // Picked from the autocomplete: already linked to a master and filed. Trust it as-is.
        if (skill.taxonomyId() != null && skill.category() != null) return skill;

        Optional<SkillTaxonomy> match = resolver.master(skill.skillName());
        if (match.isEmpty()) return skill;

        SkillTaxonomy t = match.get();
        return new ProfileSkill(
                skill.id(), skill.userId(),
                t.name(),                              // collapse onto the master's canonical spelling
                skill.taxonomyId() != null ? skill.taxonomyId() : t.id(),
                skill.proficiencyLevel(), skill.yearsExperience(), skill.usedInProduction(),
                skill.displayOrder(),
                skill.category() != null ? skill.category() : t.category());
    }

    /**
     * Folds a newly-added skill into one the user already holds under the same master: keeps the
     * existing row's identity and display order, but takes any detail the incoming add supplied
     * (proficiency, years, production use, taxonomy link, category) where the existing row lacked it.
     */
    private static ProfileSkill mergeInto(ProfileSkill existing, ProfileSkill incoming) {
        return new ProfileSkill(
                existing.id(), existing.userId(), existing.skillName(),
                existing.taxonomyId() != null ? existing.taxonomyId() : incoming.taxonomyId(),
                incoming.proficiencyLevel() != null ? incoming.proficiencyLevel() : existing.proficiencyLevel(),
                incoming.yearsExperience() != null ? incoming.yearsExperience() : existing.yearsExperience(),
                incoming.usedInProduction() || existing.usedInProduction(),
                existing.displayOrder(),
                existing.category() != null ? existing.category() : incoming.category());
    }
}
