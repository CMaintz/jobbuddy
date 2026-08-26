package com.autoapplicant.domain.company;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One tracked unsolicited outreach: who, through which channel, when, and when to come back.
 *
 * @param companyName kept alongside {@code companyId} so the record survives the company row
 * @param contactName the person written to, when the posting or the user named one
 * @param followUpDue the date to come back — the field that makes the difference between a list
 *                    of sent letters and an actual outreach process
 */
public record OutreachContact(
        UUID id,
        UUID userId,
        UUID companyId,
        String companyName,
        OutreachStatus status,
        String channel,
        String contactName,
        Instant contactedAt,
        LocalDate followUpDue,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    /** True when the follow-up date has arrived and the thread is still open. */
    public boolean isFollowUpDue(LocalDate today) {
        return followUpDue != null && status != OutreachStatus.CLOSED && !followUpDue.isAfter(today);
    }
}
