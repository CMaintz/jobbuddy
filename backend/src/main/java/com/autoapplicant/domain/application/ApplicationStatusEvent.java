package com.autoapplicant.domain.application;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable status-transition event. The append-only ledger of these events is the
 * source for funnel-velocity and time-in-stage analytics. {@code fromStatus} is null for
 * the very first event of an application.
 */
public record ApplicationStatusEvent(
        UUID id,
        UUID applicationId,
        UUID userId,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        Instant occurredAt
) {}
