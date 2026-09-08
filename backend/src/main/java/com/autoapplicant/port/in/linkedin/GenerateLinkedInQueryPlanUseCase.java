package com.autoapplicant.port.in.linkedin;

import com.autoapplicant.domain.linkedin.LinkedInQueryPlan;

import java.util.List;
import java.util.UUID;

public interface GenerateLinkedInQueryPlanUseCase {

    /** (Re)generate and persist the LinkedIn keyword plan for a single user's profile. */
    LinkedInQueryPlan generateForUser(UUID userId);

    /**
     * Ensure every profile has a plan no older than the configured staleness window,
     * regenerating the stale/missing ones. Returns all current plans. Best-effort:
     * a failure to generate one user's plan does not abort the others.
     */
    List<LinkedInQueryPlan> ensureFreshPlans();
}
