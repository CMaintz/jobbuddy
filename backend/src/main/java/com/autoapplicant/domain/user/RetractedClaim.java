package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.UUID;

/** A claim the user has explicitly disowned; it must never resurface in generated content. */
public record RetractedClaim(UUID id, UUID userId, String claim, String reason, Instant createdAt) {}
