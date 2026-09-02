package com.autoapplicant.domain.application;

import java.time.Instant;

/**
 * One step in an application's progress — both the moves the user makes (saving a role,
 * sending it) and the employer's replies. Built from the append-only status-event ledger,
 * so it is the whole history rather than the half of it the employer drove.
 *
 * <p>{@code fromStatus} is null for the first entry, which is the application being created.
 */
public record ApplicationTimelineEntry(
        Instant at,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        boolean employerResponse,
        String notes
) {}
