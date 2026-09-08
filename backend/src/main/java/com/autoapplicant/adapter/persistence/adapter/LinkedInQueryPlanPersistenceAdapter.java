package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.LinkedInQueryPlanEntity;
import com.autoapplicant.adapter.persistence.repository.LinkedInQueryPlanJpaRepository;
import com.autoapplicant.domain.linkedin.LinkedInQueryPlan;
import com.autoapplicant.port.out.linkedin.LinkedInQueryPlanRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class LinkedInQueryPlanPersistenceAdapter implements LinkedInQueryPlanRepositoryPort {

    private final LinkedInQueryPlanJpaRepository repo;

    public LinkedInQueryPlanPersistenceAdapter(LinkedInQueryPlanJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public LinkedInQueryPlan save(LinkedInQueryPlan plan) {
        LinkedInQueryPlanEntity e = repo.findByUserId(plan.userId()).orElse(new LinkedInQueryPlanEntity());
        e.setUserId(plan.userId());
        e.setKeywords(toArray(plan.keywords()));
        e.setBreadth(plan.breadth());
        e.setGeneratedAt(plan.generatedAt() != null ? plan.generatedAt() : java.time.Instant.now());
        return toDomain(repo.save(e));
    }

    @Override
    public Optional<LinkedInQueryPlan> findByUserId(UUID userId) {
        return repo.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public List<LinkedInQueryPlan> findAll() {
        return repo.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    private LinkedInQueryPlan toDomain(LinkedInQueryPlanEntity e) {
        return new LinkedInQueryPlan(e.getId(), e.getUserId(), toList(e.getKeywords()),
                e.getBreadth(), e.getGeneratedAt());
    }

    private static List<String> toList(String[] arr) { return arr != null ? Arrays.asList(arr) : List.of(); }
    private static String[] toArray(List<String> l) { return l != null ? l.toArray(String[]::new) : new String[0]; }
}
