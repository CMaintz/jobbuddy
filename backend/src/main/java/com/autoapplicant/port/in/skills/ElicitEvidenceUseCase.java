package com.autoapplicant.port.in.skills;

import com.autoapplicant.domain.skill.EvidenceDraft;
import com.autoapplicant.domain.skill.EvidenceGap;

import java.util.List;
import java.util.UUID;

public interface ElicitEvidenceUseCase {

    /**
     * Follow-up questions for a batch of gaps, tailored to what the profile already says. One model
     * call for the whole batch; falls back to the deterministic template on any failure, so the
     * feature works with the AI disabled or unavailable.
     */
    List<EvidenceGap> tailorQuestions(UUID userId, List<EvidenceGap> gaps);

    /**
     * Restructures a free-text answer into situation / action / result for the user to confirm.
     * The model may only reorganise what the user wrote — the draft is checked against their own
     * words before it is returned.
     */
    EvidenceDraft draftFromAnswer(UUID userId, String skillName, String freeText);
}
