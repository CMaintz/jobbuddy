package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.RetractedClaim;
import com.autoapplicant.port.in.user.ManageRetractedClaimsUseCase;
import com.autoapplicant.port.out.user.RetractedClaimRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RetractedClaimService implements ManageRetractedClaimsUseCase {

    private final RetractedClaimRepositoryPort repo;

    public RetractedClaimService(RetractedClaimRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<RetractedClaim> list(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public RetractedClaim add(UUID userId, String claim, String reason) {
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("Retracted claim text must not be blank");
        }
        return repo.save(new RetractedClaim(null, userId, claim.trim(), reason, Instant.now()));
    }

    @Override
    public void remove(UUID userId, UUID claimId) {
        repo.delete(claimId, userId);
    }
}
