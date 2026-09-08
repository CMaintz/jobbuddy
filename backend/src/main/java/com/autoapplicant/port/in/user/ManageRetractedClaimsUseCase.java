package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.RetractedClaim;

import java.util.List;
import java.util.UUID;

public interface ManageRetractedClaimsUseCase {
    List<RetractedClaim> list(UUID userId);
    RetractedClaim add(UUID userId, String claim, String reason);
    void remove(UUID userId, UUID claimId);
}
