package com.autoapplicant.port.in.notification;

import com.autoapplicant.domain.notification.Nudge;

import java.util.List;
import java.util.UUID;

public interface GetNudgesUseCase {
    /** Computed follow-up and deadline nudges for the user's pipeline, most urgent first. */
    List<Nudge> getNudges(UUID userId);
}
