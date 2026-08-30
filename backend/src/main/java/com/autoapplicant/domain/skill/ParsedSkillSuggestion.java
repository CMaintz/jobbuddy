package com.autoapplicant.domain.skill;

import java.util.UUID;

/**
 * A skill a parsed document demonstrates without naming, queued for the user to confirm.
 *
 * <p>The parsers themselves stay strictly extractive — what they write into the profile is what
 * the fact guard later treats as the candidate's own account, so a skill invented there would be
 * permanently "supported" everywhere downstream. An inference is a different kind of claim: it is
 * the parser's reading, not the document's statement, and it belongs in front of the only person
 * who can say whether it is true.
 *
 * @param evidence what in the document implies the skill, in the document's own words. A
 *                 suggestion without it is unarguable, and an unarguable suggestion gets clicked
 *                 through rather than read.
 */
public record ParsedSkillSuggestion(
        UUID id,
        UUID userId,
        String skillName,
        String normalizedName,
        String evidence,
        Source source) {

    public enum Source { CV_PARSE, LINKEDIN_PARSE }
}
