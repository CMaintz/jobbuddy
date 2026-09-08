package com.autoapplicant.domain.notification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A computed "needs attention" item derived from the user's pipeline —
 * not stored, recomputed on request.
 */
public record Nudge(
        NudgeType type,
        UUID applicationId,
        UUID jobId,
        String jobTitle,
        String companyName,
        /** Set for DEADLINE_SOON: the posting's application deadline. */
        LocalDate deadline,
        /** Set for FOLLOW_UP: days since the application was sent. */
        Integer daysSinceApplied
) {
    public enum NudgeType {
        /** A saved/in-preparation job's application deadline is within the next 7 days. */
        DEADLINE_SOON,
        /** Applied 14+ days ago with no reply — time to send a follow-up. */
        FOLLOW_UP
    }
}
