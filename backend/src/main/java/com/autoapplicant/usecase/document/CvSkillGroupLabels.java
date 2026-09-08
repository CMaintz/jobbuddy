package com.autoapplicant.usecase.document;

import java.util.Locale;
import java.util.Map;

/**
 * Turns a taxonomy category into the heading it should carry on a CV — or into nothing, when it
 * should not head a group at all.
 *
 * <p>Three vocabularies met here and none of them agreed. {@code skill_taxonomy.category} is a
 * classification vocabulary ("Programming Language", "Soft Skill", "Methodology"); the
 * resume-builder's own datalist offers CV headings ("Frameworks", "Tools", "Practices"); and
 * the grouping code renders whatever string it is given verbatim, as {@code "<category>:  Java"}.
 * So a skill categorised from the taxonomy printed "Programming Language:" on the finished CV,
 * while a skill the user typed in the builder printed "Languages:" — two headings, same meaning,
 * on the same document.
 *
 * <p>{@code Soft Skill} maps to nothing on purpose. The CV persona is explicit that soft skills are
 * never listed but woven into the experience descriptions, and the Danish playbook agrees, so a
 * "Soft Skill:" heading would have the document contradict the prompt that produced it. Suppressing
 * the heading does not drop the skill — it falls into the trailing uncategorised line — because
 * what to include is the tailoring step's decision, not this one's.
 *
 * <p>This is presentation only. {@link com.autoapplicant.domain.document.structured.CareerProfileForAi}
 * keeps the raw taxonomy categories, which are better signal for the model: knowing something is a
 * soft skill is exactly what lets it weave rather than list.
 */
final class CvSkillGroupLabels {

    /** Categories that should not head a group, whatever the language. */
    private static final String SOFT_SKILL = "soft skill";

    private static final Map<String, String> ENGLISH = Map.ofEntries(
            // Not "Languages": the CV already has a Languages section, for the ones the
            // candidate speaks. Two headings reading "Languages" on one page, one listing Java
            // and the other Danish, is worse than the extra word.
            Map.entry("programming language", "Programming Languages"),
            Map.entry("framework", "Frameworks"),
            Map.entry("library", "Libraries"),
            Map.entry("database", "Databases"),
            Map.entry("cloud", "Cloud"),
            Map.entry("devops", "DevOps"),
            Map.entry("tool", "Tools"),
            Map.entry("api", "APIs"),
            Map.entry("ai/ml", "AI/ML"),
            Map.entry("architecture", "Architecture"),
            Map.entry("testing", "Testing"),
            Map.entry("security", "Security"),
            Map.entry("methodology", "Practices"),
            Map.entry("domain", "Domain"));

    private static final Map<String, String> DANISH = Map.ofEntries(
            Map.entry("programming language", "Programmeringssprog"),
            Map.entry("language", "Programmeringssprog"),
            Map.entry("framework", "Frameworks"),
            Map.entry("library", "Biblioteker"),
            Map.entry("database", "Databaser"),
            Map.entry("cloud", "Cloud"),
            Map.entry("devops", "DevOps"),
            Map.entry("tool", "Værktøjer"),
            Map.entry("api", "API'er"),
            Map.entry("ai/ml", "AI/ML"),
            Map.entry("architecture", "Arkitektur"),
            Map.entry("testing", "Test"),
            Map.entry("security", "Sikkerhed"),
            Map.entry("methodology", "Metoder"),
            Map.entry("domain", "Fagområde"));

    private final Map<String, String> labels;

    private CvSkillGroupLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    static CvSkillGroupLabels forLanguage(String language) {
        return new CvSkillGroupLabels(JobLanguageDetector.isDanish(language) ? DANISH : ENGLISH);
    }

    /**
     * The CV heading for this category, or null when the skill should carry no group.
     *
     * <p>A category the taxonomy does not know — one the user typed themselves — is kept as they
     * wrote it. They chose that word for this CV, and second-guessing it would be worse than an
     * inconsistent heading.
     */
    String labelFor(String taxonomyCategory) {
        if (taxonomyCategory == null || taxonomyCategory.isBlank()) return null;
        String key = taxonomyCategory.strip().toLowerCase(Locale.ROOT);
        if (SOFT_SKILL.equals(key)) return null;
        String mapped = labels.get(key);
        return mapped != null ? mapped : taxonomyCategory.strip();
    }
}
