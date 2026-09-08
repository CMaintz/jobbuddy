package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.SkillCandidateDismissalEntity;
import com.autoapplicant.adapter.persistence.repository.SkillCandidateDismissalJpaRepository;
import com.autoapplicant.port.out.skills.SkillCandidateDismissalRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SkillCandidateDismissalPersistenceAdapter implements SkillCandidateDismissalRepositoryPort {

    private final SkillCandidateDismissalJpaRepository repo;

    public SkillCandidateDismissalPersistenceAdapter(SkillCandidateDismissalJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Set<String> findDismissedNames(UUID userId) {
        return repo.findByUserId(userId).stream()
                .map(SkillCandidateDismissalEntity::getNormalizedName)
                .collect(Collectors.toSet());
    }

    @Override
    public void dismiss(UUID userId, String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) return;
        // Dismissing twice is the same decision, and the unique index would reject the second row.
        if (repo.existsByUserIdAndNormalizedName(userId, normalizedName)) return;
        SkillCandidateDismissalEntity e = new SkillCandidateDismissalEntity();
        e.setUserId(userId);
        e.setNormalizedName(normalizedName);
        repo.save(e);
    }
}
