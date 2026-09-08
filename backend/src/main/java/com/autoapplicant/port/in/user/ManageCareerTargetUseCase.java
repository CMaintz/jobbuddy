package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.CareerTarget;

import java.util.UUID;

public interface ManageCareerTargetUseCase {
    /** The user's career target, or an empty default when none has been set. */
    CareerTarget get(UUID userId);

    /**
     * Creates or replaces the user's career target. {@code draft}'s {@code userId} and
     * {@code updatedAt} are ignored — the caller's authenticated id and the server clock win.
     */
    CareerTarget upsert(UUID userId, CareerTarget draft);
}
