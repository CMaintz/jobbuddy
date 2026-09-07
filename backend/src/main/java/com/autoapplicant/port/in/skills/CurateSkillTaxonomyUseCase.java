package com.autoapplicant.port.in.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.skill.TaxonomyCandidate;

import java.util.List;

/**
 * Growing the skill taxonomy from what the job market actually names.
 *
 * <p>Administration, not a user feature: these rows are the vocabulary every user picks from, so
 * one person curates them and everyone benefits. Nothing here touches anybody's profile.
 */
public interface CurateSkillTaxonomyUseCase {

    /** Labels the market names that the taxonomy does not know, most-demanded first. */
    List<TaxonomyCandidate> candidates(int limit);

    /**
     * Adds a candidate to the taxonomy under the given category.
     *
     * @throws IllegalArgumentException when the name or category is blank
     */
    SkillTaxonomy approve(String name, String category);

    /** Rules a label out of the taxonomy for good, so the queue stops offering it. */
    void reject(String name, String reason);
}
