package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Folds the many surface forms of one skill onto a single key, so a profile that says
 * "Kubernetes" and a posting that says "k8s" are recognised as the same thing.
 *
 * <p>This is deliberately <em>not</em> stemming. Stemming over-reports — it would let "he excels
 * at" satisfy "Excel" — and it still would not fold "k8s" onto "kubernetes" or "postgres" onto
 * "postgresql", because those are curated abbreviations, not inflections. The shared vocabulary is
 * the skill taxonomy: each row's {@code aliases} list names the forms it also answers to, and every
 * one of them resolves to that row's normalised name.
 *
 * <p>Two views of the same data:
 * <ul>
 *   <li>{@link #canonical(String)} — for set/equality matching (does the profile hold this skill?):
 *       collapse a label to one key so both sides compare on the same token.</li>
 *   <li>{@link #surfaceForms(String)} — for text matching (does this document mention the skill?):
 *       expand a label to every form worth searching for, so a CV saying "k8s" still counts as
 *       covering a "Kubernetes" requirement.</li>
 * </ul>
 *
 * <p>The map is built once from the taxonomy (a few hundred reference rows) and cached. Aliases
 * only change via migration today; {@link #refresh()} exists so a future admin-curation path can
 * invalidate the cache after an edit.
 */
@Component
public class SkillCanonicalizer {

    private final SkillTaxonomyRepositoryPort taxonomy;

    /** normalised label → canonical key (a row's normalised name). */
    private volatile Map<String, String> forwardMap;
    /** canonical key → the raw forms worth searching a document for (name + aliases). */
    private volatile Map<String, List<String>> groupMap;

    public SkillCanonicalizer(SkillTaxonomyRepositoryPort taxonomy) {
        this.taxonomy = taxonomy;
    }

    /**
     * The canonical key for a label — a taxonomy row's normalised name when the label (or one of
     * its aliases) is known, otherwise the label normalised as-is. Unknown labels map to
     * themselves, so nothing is ever silently merged onto the wrong skill.
     */
    public String canonical(String label) {
        String normalized = SkillNames.normalize(label);
        if (normalized.isEmpty()) return normalized;
        return forward().getOrDefault(normalized, normalized);
    }

    /**
     * Every form of a label worth searching a document for: the label itself plus, when the label
     * is known, the canonical name and all aliases in its group. Matching is case-insensitive
     * downstream, so the raw forms are returned as stored.
     */
    public List<String> surfaceForms(String label) {
        if (label == null || label.isBlank()) return List.of();
        LinkedHashSet<String> forms = new LinkedHashSet<>();
        forms.add(label.strip());
        List<String> group = groups().get(canonical(label));
        if (group != null) forms.addAll(group);
        return List.copyOf(forms);
    }

    /** Drop the cached maps; the next lookup rebuilds them. For a future admin-curation path. */
    public void refresh() {
        forwardMap = null;
        groupMap = null;
    }

    private Map<String, String> forward() {
        if (forwardMap == null) build();
        return forwardMap;
    }

    private Map<String, List<String>> groups() {
        if (groupMap == null) build();
        return groupMap;
    }

    private synchronized void build() {
        if (forwardMap != null && groupMap != null) return;
        List<SkillTaxonomy> rows = taxonomy.findAll();
        Map<String, String> forward = new java.util.HashMap<>();
        Map<String, List<String>> groups = new java.util.HashMap<>();

        seedCanonicalKeys(rows, forward);
        registerAliases(rows, forward);
        buildGroups(rows, groups);

        this.groupMap = groups;
        this.forwardMap = forward;
    }

    /**
     * Pass 1: every row's own names are authoritative keys. Done first so a distinct taxonomy row
     * can never be captured as another row's alias, whatever order {@code findAll()} returns.
     */
    private static void seedCanonicalKeys(List<SkillTaxonomy> rows, Map<String, String> forward) {
        for (SkillTaxonomy row : rows) {
            String key = SkillNames.normalize(row.normalizedName());
            if (key.isEmpty()) continue;
            forward.put(key, key);
            if (row.name() != null) forward.put(SkillNames.normalize(row.name()), key);
        }
    }

    /**
     * Pass 2: every alias resolves to its row's key, but only where it doesn't shadow a real row
     * seeded in Pass 1 ({@code putIfAbsent}).
     */
    private static void registerAliases(List<SkillTaxonomy> rows, Map<String, String> forward) {
        for (SkillTaxonomy row : rows) {
            String key = SkillNames.normalize(row.normalizedName());
            if (key.isEmpty() || row.aliases() == null) continue;
            for (String alias : row.aliases()) {
                if (alias != null && !alias.isBlank()) {
                    forward.putIfAbsent(SkillNames.normalize(alias), key);
                }
            }
        }
    }

    /** Pass 3: record each row's raw searchable forms (name, normalised name, aliases) as its group. */
    private static void buildGroups(List<SkillTaxonomy> rows, Map<String, List<String>> groups) {
        for (SkillTaxonomy row : rows) {
            String key = SkillNames.normalize(row.normalizedName());
            if (key.isEmpty()) continue;
            groups.put(key, surfaceFormsOf(row));
        }
    }

    /** The raw searchable forms for one taxonomy row: its display name, normalised name and aliases. */
    private static List<String> surfaceFormsOf(SkillTaxonomy row) {
        LinkedHashSet<String> rawForms = new LinkedHashSet<>();
        if (row.name() != null && !row.name().isBlank()) rawForms.add(row.name().strip());
        rawForms.add(row.normalizedName());
        if (row.aliases() != null) {
            for (String alias : row.aliases()) {
                if (alias != null && !alias.isBlank()) rawForms.add(alias.strip());
            }
        }
        return List.copyOf(rawForms);
    }
}
