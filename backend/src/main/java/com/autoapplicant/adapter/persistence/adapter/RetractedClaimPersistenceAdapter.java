package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.RetractedClaimEntity;
import com.autoapplicant.adapter.persistence.repository.RetractedClaimJpaRepository;
import com.autoapplicant.domain.user.RetractedClaim;
import com.autoapplicant.port.out.user.RetractedClaimRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class RetractedClaimPersistenceAdapter implements RetractedClaimRepositoryPort {

    private final RetractedClaimJpaRepository repo;

    public RetractedClaimPersistenceAdapter(RetractedClaimJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<RetractedClaim> findByUserId(UUID userId) {
        return repo.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public RetractedClaim save(RetractedClaim claim) {
        RetractedClaimEntity e = claim.id() != null
                ? repo.findById(claim.id()).orElseGet(RetractedClaimEntity::new)
                : new RetractedClaimEntity();
        e.setUserId(claim.userId());
        e.setClaim(claim.claim());
        e.setReason(claim.reason());
        return toDomain(repo.save(e));
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private RetractedClaim toDomain(RetractedClaimEntity e) {
        return new RetractedClaim(e.getId(), e.getUserId(), e.getClaim(), e.getReason(), e.getCreatedAt());
    }
}
