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
        /** Set for OUTREACH_FOLLOW_UP: the tracked outreach that is due. */
        UUID outreachId,
        /** Set for REMINDER_DUE: the reminder the user set. */
        UUID reminderId,
        String jobTitle,
        String companyName,
        /** Set for DEADLINE_SOON and the two due types: the date it is due. */
        LocalDate deadline,
        /** Set for FOLLOW_UP: days since the application was sent. */
        Integer daysSinceApplied,
        /** Set for REMINDER_DUE: what the user wrote when they set it. */
        String note
) {
    public enum NudgeType {
        /** A saved/in-preparation job's application deadline is within the next 7 days. */
        DEADLINE_SOON,
        /** Applied 14+ days ago with no reply — time to send a follow-up. */
        FOLLOW_UP,
        /** A reminder the user set on an application has come due. */
        REMINDER_DUE,
        /** A tracked unsolicited outreach has reached its follow-up date. */
        OUTREACH_FOLLOW_UP
    }

    /** An application-shaped nudge — the two original types. */
    public static Nudge forApplication(NudgeType type, UUID applicationId, UUID jobId, String jobTitle,
                                       String companyName, LocalDate deadline, Integer daysSinceApplied) {
        return new Nudge(type, applicationId, jobId, null, null, jobTitle, companyName,
                deadline, daysSinceApplied, null);
    }
}
