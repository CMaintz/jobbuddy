package com.autoapplicant.domain.application;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable status-transition event. The append-only ledger of these events is the
 * source for the progress timeline and for funnel-velocity analytics. {@code fromStatus}
 * is null for the very first event of an application — its creation.
 *
 * <p>{@code notes} is whatever the candidate wrote about this particular move.
 */
public record ApplicationStatusEvent(
        UUID id,
        UUID applicationId,
        UUID userId,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        Instant occurredAt,
        String notes
) {}
