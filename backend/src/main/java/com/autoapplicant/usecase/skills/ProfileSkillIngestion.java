package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a list of skill names into profile skills.
 *
 * <p>This is how a parsed CV or LinkedIn export gets its skills onto the profile. It exists
 * because those documents used to write bare names into a text[] column while manual entry wrote
 * structured rows — two shapes for the same thing, so a skill's category, proficiency and taxonomy
 * link all depended on how it happened to arrive. Anyone who onboarded by uploading a CV had none
 * of them, which silently disabled every feature built on top: grouped skill sections, the
 * proficiency weighting in matching, category-aware suggestions.
 *
 * <p>Names are resolved against the taxonomy so an imported "kubernetes" lands on the same row as
 * a hand-picked Kubernetes. A name the taxonomy has never heard of is still kept — a real skill
 * the seed list does not know is not a reason to drop it — it simply carries no category.
 *
 * <p>Nothing here invents a skill or a proficiency. The document said the candidate has these; how
 * deeply, only they can say, so proficiency is left unset rather than guessed.
 */
@Service
public class ProfileSkillIngestion {

    private static final Logger log = LoggerFactory.getLogger(ProfileSkillIngestion.class);

    private final ProfileSkillRepositoryPort skillRepo;
    private final SkillResolver resolver;

    public ProfileSkillIngestion(ProfileSkillRepositoryPort skillRepo, SkillResolver resolver) {
        this.skillRepo = skillRepo;
        this.resolver = resolver;
    }

    /**
     * Adds any of these names the profile does not already have, and returns everything the
     * profile holds afterwards.
     *
     * <p>Additive on purpose: re-importing a CV must not delete the skills someone added by hand,
     * nor reset what they recorded about the ones it does mention. An existing skill is left
     * exactly as it is.
     */
    public List<ProfileSkill> ingest(UUID userId, List<String> names) {
        List<ProfileSkill> existing = skillRepo.findByUserId(userId);
        if (names == null || names.isEmpty()) return existing;

        List<String> fresh = freshNames(existing, names);
        if (fresh.isEmpty()) return existing;

        List<ProfileSkill> all = new ArrayList<>(existing);
        all.addAll(saveNewSkills(userId, fresh, existing.size()));
        return all;
    }

    /**
     * The stripped names this profile does not already hold, in input order.
     *
     * <p>Dedup on the canonical key, so an imported "k8s" is recognised as already held when the
     * profile has Kubernetes — and two aliases of one skill in the same document count once.
     */
    private List<String> freshNames(List<ProfileSkill> existing, List<String> names) {
        Set<String> held = new HashSet<>();
        existing.forEach(s -> held.add(resolver.key(s.skillName())));
        return names.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::strip)
                .filter(n -> held.add(resolver.key(n)))
                .toList();
    }

    /**
     * Persists each fresh name as a profile skill — resolved onto its taxonomy master where one
     * exists, appended after the profile's existing skills. One unsaveable skill is logged and
     * skipped rather than costing the user the whole import.
     */
    private List<ProfileSkill> saveNewSkills(UUID userId, List<String> fresh, int startOrder) {
        Map<String, SkillTaxonomy> masters = resolver.mastersByKey(fresh);
        List<ProfileSkill> added = new ArrayList<>();
        int order = startOrder;
        for (String name : fresh) {
            SkillTaxonomy master = masters.get(resolver.key(name));
            try {
                added.add(skillRepo.save(new ProfileSkill(
                        null, userId,
                        master != null ? master.name() : name,   // collapse onto the master's spelling
                        master != null ? master.id() : null,
                        null,                       // proficiency: the document does not say
                        null,                       // years: nor this
                        false,
                        order++,
                        master != null ? master.category() : null)));
            } catch (Exception e) {
                log.warn("Could not add parsed skill '{}' for user {}: {}", name, userId, e.getMessage());
            }
        }
        return added;
    }
}
