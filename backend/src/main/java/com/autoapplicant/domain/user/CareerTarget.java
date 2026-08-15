package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A candidate's declared career targeting — the persistent layer behind archetype-aware
 * generation. All fields are identity-free and safe to send to the AI.
 *
 * @param targetArchetypes    role archetypes the candidate is aiming for (e.g. "Platform Engineer",
 *                            "AI Implementation", "Marketing Communications")
 * @param northStar           a one-line statement of the ideal next role / direction
 * @param narrative           positioning narrative distinct from the CV summary (the "why me / why this")
 * @param cultureRequirements company-culture criteria used in job evaluation
 */
public record CareerTarget(
        UUID userId,
        List<String> targetArchetypes,
        String northStar,
        String narrative,
        List<String> cultureRequirements,
        Instant updatedAt
) {}
