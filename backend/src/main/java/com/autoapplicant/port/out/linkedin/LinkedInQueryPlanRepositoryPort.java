package com.autoapplicant.port.out.linkedin;

import com.autoapplicant.domain.linkedin.LinkedInQueryPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedInQueryPlanRepositoryPort {
    LinkedInQueryPlan save(LinkedInQueryPlan plan);
    Optional<LinkedInQueryPlan> findByUserId(UUID userId);
    List<LinkedInQueryPlan> findAll();
}
