package com.autoapplicant.port.in.skills;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillCandidate;
import com.autoapplicant.domain.skill.SkillConfirmation;

import java.util.List;
import java.util.UUID;

public interface SuggestSkillCandidatesUseCase {

    /**
     * Skills worth asking the user about, best first. Deterministic — no AI call — so it is free
     * to call on every page load.
     */
    List<SkillCandidate> suggest(UUID userId, int limit);

    /**
     * Records the user's answers: accepted skills are added to the profile, declined ones are
     * never offered again, skipped ones come back next time. Returns the skills that were added.
     */
    List<ProfileSkill> confirm(UUID userId, List<SkillConfirmation> confirmations);
}
