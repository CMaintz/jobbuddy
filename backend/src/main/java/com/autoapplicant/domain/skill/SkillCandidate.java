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
 */
public record SkillCandidate(
        String name,
        String category,
        int marketFrequency,
        List<String> relatedSkills,
        SkillCandidateSource source) {

    public enum SkillCandidateSource {
        /** Sits next to a skill the user already has, in the seeded taxonomy. */
        TAXONOMY_ADJACENT,
        /** Named by postings the user matches, whether or not the taxonomy knows it. */
        MARKET_DEMAND,
        /** Both — the strongest kind of candidate. */
        BOTH
    }
}
