package com.autoapplicant.domain.user;

import java.util.UUID;

/** Published after a user's profile has been saved, so data derived from it can be refreshed. */
public record ProfileSavedEvent(UUID userId, Profile profile) {}
