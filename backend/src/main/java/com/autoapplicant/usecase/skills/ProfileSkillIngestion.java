package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private final SkillTaxonomyRepositoryPort taxonomyRepo;

    public ProfileSkillIngestion(ProfileSkillRepositoryPort skillRepo,
                                 SkillTaxonomyRepositoryPort taxonomyRepo) {
        this.skillRepo = skillRepo;
        this.taxonomyRepo = taxonomyRepo;
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

        Set<String> held = new HashSet<>();
        existing.forEach(s -> held.add(normalize(s.skillName())));

        List<String> fresh = names.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::strip)
                .filter(n -> held.add(normalize(n)))   // dedupes within the batch too
                .toList();
        if (fresh.isEmpty()) return existing;

        Map<String, SkillTaxonomy> taxonomy = lookup(fresh);

        List<ProfileSkill> added = new ArrayList<>();
        int order = existing.size();
        for (String name : fresh) {
            SkillTaxonomy match = taxonomy.get(normalize(name));
            try {
                added.add(skillRepo.save(new ProfileSkill(
                        null, userId, name,
                        match != null ? match.id() : null,
                        null,                       // proficiency: the document does not say
                        null,                       // years: nor this
                        false,
                        order++,
                        match != null ? match.category() : null)));
            } catch (Exception e) {
                // One unsaveable skill must not cost the user the whole import.
                log.warn("Could not add parsed skill '{}' for user {}: {}", name, userId, e.getMessage());
            }
        }

        List<ProfileSkill> all = new ArrayList<>(existing);
        all.addAll(added);
        return all;
    }

    private Map<String, SkillTaxonomy> lookup(List<String> names) {
        Set<String> normalized = new HashSet<>();
        names.forEach(n -> normalized.add(normalize(n)));
        Map<String, SkillTaxonomy> byName = new HashMap<>();
        taxonomyRepo.findByNormalizedNames(normalized)
                .forEach(t -> byName.putIfAbsent(t.normalizedName(), t));
        return byName;
    }

    private static String normalize(String value) {
        return SkillNames.normalize(value);
    }
}
