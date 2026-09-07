package com.autoapplicant.domain.skill;

/**
 * How often one label appears across the postings we hold, as enrichment extracted it.
 *
 * <p>{@code technologyPostings} is the share that arrived in a posting's {@code technologies}
 * array rather than its {@code skills} array. It is evidence about the label, not a verdict: the
 * category a taxonomy row gets is the admin's to choose, and this only says which drawer
 * enrichment kept putting it in.
 *
 * @param label              the most common spelling across the postings that name it
 * @param postings           how many distinct postings name it
 * @param technologyPostings how many of those named it as a technology
 */
public record SkillMention(String label, int postings, int technologyPostings) {

    /** Enrichment more often than not read this as a tool rather than a competency. */
    public boolean readAsTechnology() {
        return technologyPostings * 2 >= postings;
    }
}
