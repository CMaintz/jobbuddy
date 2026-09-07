package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.SkillTaxonomyRejectionEntity;
import com.autoapplicant.adapter.persistence.repository.SkillTaxonomyRejectionJpaRepository;
import com.autoapplicant.port.out.skills.TaxonomyRejectionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class TaxonomyRejectionPersistenceAdapter implements TaxonomyRejectionRepositoryPort {

    private final SkillTaxonomyRejectionJpaRepository repo;

    public TaxonomyRejectionPersistenceAdapter(SkillTaxonomyRejectionJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Set<String> findRejectedNormalizedNames() {
        return new LinkedHashSet<>(repo.findAllNormalizedNames());
    }

    @Override
    public void reject(String normalizedName, String label, String reason) {
        SkillTaxonomyRejectionEntity entity = repo.findById(normalizedName)
                .orElseGet(SkillTaxonomyRejectionEntity::new);
        entity.setNormalizedName(normalizedName);
        entity.setLabel(label);
        entity.setReason(reason);
        repo.save(entity);
    }
}
