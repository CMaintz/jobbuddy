package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.RetractedClaim;

import java.util.List;
import java.util.UUID;

public interface RetractedClaimRepositoryPort {
    List<RetractedClaim> findByUserId(UUID userId);
    RetractedClaim save(RetractedClaim claim);
    void delete(UUID id, UUID userId);
}
