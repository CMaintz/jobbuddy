package com.autoapplicant.domain.user;

import java.util.UUID;

/**
 * Published after a user's profile has been saved, so data derived from it can be refreshed.
 *
 * <p>Carries no profile snapshot on purpose: listeners run asynchronously and must read the
 * current state, or a slow handler for an older save can overwrite a newer one.
 */
public record ProfileSavedEvent(UUID userId) {}
