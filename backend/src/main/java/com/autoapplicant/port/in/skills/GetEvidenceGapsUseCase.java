package com.autoapplicant.port.in.skills;

import com.autoapplicant.domain.skill.EvidenceGap;
import com.autoapplicant.domain.user.InterviewStory;

import java.util.List;
import java.util.UUID;

public interface GetEvidenceGapsUseCase {

    /** Claimed, in-demand skills with no story behind them — most-demanded first. */
    List<EvidenceGap> evidenceGaps(UUID userId, int limit);

    /**
     * Answers one gap: stores the evidence as a STAR story tagged with the skill, so generation
     * and interview prep can both draw on it.
     */
    InterviewStory recordEvidence(UUID userId, String skillName, String situation,
                                  String action, String result);
}
