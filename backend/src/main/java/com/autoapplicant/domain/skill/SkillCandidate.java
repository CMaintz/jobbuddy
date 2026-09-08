package com.autoapplicant.domain.skill;

import java.util.List;

/**
 * A skill the user has not claimed, offered for confirmation.
 *
 * <p>Carries the evidence for the suggestion as data rather than a sentence: the UI composes the
 * explanation in the reader's language, and the numbers are what make the suggestion arguable
 * ("appears in 7 of your matched postings" is checkable; "recommended for you" is not).
 *
 * @param marketFrequency how many of the postings this user actually matches mention it — the
 *                        reason to spend attention on this one rather than another
 * @param relatedSkills   the user's own skills it sits next to in the taxonomy; empty when the
 *                        candidate came purely from the market
 * @param source          where the candidate came from, for ordering and explanation
 * @param evidence        for a candidate inferred from a parsed document, what in that document
 *                        implies the skill, in the document's own words. Null for the others,
 *                        whose evidence is already the frequency and the related skills.
 */
public record SkillCandidate(
        String name,
        String category,
        int marketFrequency,
        List<String> relatedSkills,
        SkillCandidateSource source,
        String evidence) {

    /** A candidate whose evidence is its frequency and neighbours rather than a quoted line. */
    public SkillCandidate(String name, String category, int marketFrequency,
                          List<String> relatedSkills, SkillCandidateSource source) {
        this(name, category, marketFrequency, relatedSkills, source, null);
    }

    public enum SkillCandidateSource {
        /** Sits next to a skill the user already has, in the seeded taxonomy. */
        TAXONOMY_ADJACENT,
        /** Named by postings the user matches, whether or not the taxonomy knows it. */
        MARKET_DEMAND,
        /** Both — the strongest kind of candidate. */
        BOTH,
        /**
         * Implied by the user's own CV or LinkedIn export without being named there. The strongest
         * evidence of all — it is the candidate's own document — which is why it is offered rather
         * than written straight into the profile: it is the parser's reading, not the document's
         * statement.
         */
        DOCUMENT_INFERRED
    }
}
