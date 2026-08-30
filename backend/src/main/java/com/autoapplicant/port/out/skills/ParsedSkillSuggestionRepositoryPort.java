package com.autoapplicant.port.out.skills;

import com.autoapplicant.domain.skill.ParsedSkillSuggestion;

import java.util.List;
import java.util.UUID;

public interface ParsedSkillSuggestionRepositoryPort {

    List<ParsedSkillSuggestion> findByUserId(UUID userId);

    /**
     * Records the suggestion, or leaves the existing one alone when this skill is already queued.
     * Re-importing a CV should not produce a second copy of the same question.
     */
    void record(ParsedSkillSuggestion suggestion);

    /** Removes the queued suggestion once the user has answered it, either way. */
    void remove(UUID userId, String normalizedName);
}
