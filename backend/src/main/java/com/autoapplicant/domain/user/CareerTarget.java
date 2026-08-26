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
 * @param careerStage         self-declared career stage driving stage-appropriate framing and the
 *                            default CV section order; {@code null} = unset (treated as mid-career)
 * @param noticePeriod        current notice period ("opsigelsesvarsel") as the user states it —
 *                            free text, because real answers range from "3 måneder" to "negotiable"
 * @param earliestStartDate   the first date the candidate could start; {@code null} = unstated
 */
public record CareerTarget(
        UUID userId,
        List<String> targetArchetypes,
        String northStar,
        String narrative,
        List<String> cultureRequirements,
        CareerStage careerStage,
        String noticePeriod,
        java.time.LocalDate earliestStartDate,
        Instant updatedAt
) {}
