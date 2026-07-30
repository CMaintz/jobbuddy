package com.autoapplicant.domain.linkedin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A per-user set of LinkedIn keyword searches, generated from the user's profile
 * by the LLM query planner. The connector crosses these keywords with the
 * configured target locations to produce the actual searches it runs.
 *
 * <p>Keywords are deliberately plain single terms/phrases (no boolean operators).
 * The LinkedIn guest endpoint's support for {@code OR} is unverified, so OR-grouping
 * is left as a future connector-side optimization rather than baked into the plan.
 *
 * @param breadth {@code narrow} | {@code normal} | {@code wide} — how broad a net
 *                the planner was asked to cast for this user.
 */
public record LinkedInQueryPlan(
        UUID id,
        UUID userId,
        List<String> keywords,
        String breadth,
        Instant generatedAt
) {}
