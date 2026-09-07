package com.autoapplicant.domain.skill;

/**
 * A label the job market keeps naming that the skill taxonomy does not know.
 *
 * <p>Every candidate is something enrichment read off a real posting — the queue never invents a
 * skill, and a label nobody is hiring for never appears. Approving one adds it to the taxonomy so
 * users can pick it; rejecting one keeps it out for good.
 *
 * @param name             the most common spelling across the postings naming it
 * @param normalizedName   the lookup key, shared with every other skill path
 * @param postings         how many distinct postings name it — the whole ranking
 * @param readAsTechnology enrichment usually filed it under technologies rather than skills
 */
public record TaxonomyCandidate(String name, String normalizedName, int postings,
                                boolean readAsTechnology) {}
