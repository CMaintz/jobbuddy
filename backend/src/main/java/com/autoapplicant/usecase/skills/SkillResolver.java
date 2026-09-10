package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a raw skill name to the taxonomy "master" it belongs to, folding aliases on the way, so
 * a typed or imported "k8s" collapses onto the Kubernetes row rather than being stored as its own
 * skill. This is the storage-time counterpart to {@link SkillCanonicalizer}, which does the same
 * folding at match time.
 *
 * <p>{@link #key(String)} is the dedup key — two names that fold to the same master share it.
 * {@link #master(String)}/{@link #mastersByKey(Collection)} return the row itself, whose display
 * name and category the caller stores. A name the taxonomy has never heard of resolves to no master
 * and is kept verbatim: an unknown skill is not a reason to drop or rename it.
 */
@Component
public class SkillResolver {

    private final SkillTaxonomyRepositoryPort taxonomy;
    private final SkillCanonicalizer canonicalizer;

    public SkillResolver(SkillTaxonomyRepositoryPort taxonomy, SkillCanonicalizer canonicalizer) {
        this.taxonomy = taxonomy;
        this.canonicalizer = canonicalizer;
    }

    /** The alias-folded lookup/dedup key for a raw skill name ("" when there is no name). */
    public String key(String rawName) {
        return canonicalizer.canonical(rawName);
    }

    /** The taxonomy master a raw name resolves to — directly or via an alias — if the taxonomy knows it. */
    public Optional<SkillTaxonomy> master(String rawName) {
        String k = key(rawName);
        if (k.isEmpty()) return Optional.empty();
        return taxonomy.findByNormalizedName(k);
    }

    /**
     * Batch form of {@link #master(String)} for ingesting many names at once: canonical key → master
     * row, in one query rather than one per name.
     */
    public Map<String, SkillTaxonomy> mastersByKey(Collection<String> rawNames) {
        Set<String> keys = new HashSet<>();
        for (String name : rawNames) {
            String k = key(name);
            if (!k.isEmpty()) keys.add(k);
        }
        if (keys.isEmpty()) return Map.of();
        Map<String, SkillTaxonomy> byKey = new HashMap<>();
        taxonomy.findByNormalizedNames(keys).forEach(t -> byKey.putIfAbsent(t.normalizedName(), t));
        return byKey;
    }
}
