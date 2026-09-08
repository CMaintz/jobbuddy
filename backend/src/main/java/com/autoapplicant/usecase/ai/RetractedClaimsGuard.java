package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.user.RetractedClaim;
import com.autoapplicant.port.out.user.RetractedClaimRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Checks generated candidate-facing text against the user's retracted (disowned) claims,
 * so a claim the user has explicitly removed can never resurface in generated content.
 * Case-insensitive substring match — the disowned phrasing, wherever it appears.
 */
@Service
public class RetractedClaimsGuard {

    private final RetractedClaimRepositoryPort repo;

    public RetractedClaimsGuard(RetractedClaimRepositoryPort repo) {
        this.repo = repo;
    }

    /** Retracted claims that appear in the given text; empty when clean. */
    public List<String> findViolations(UUID userId, String text) {
        if (text == null || text.isBlank()) return List.of();
        String haystack = text.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (RetractedClaim rc : repo.findByUserId(userId)) {
            String claim = rc.claim();
            if (claim != null && !claim.isBlank()
                    && haystack.contains(claim.toLowerCase(Locale.ROOT).trim())) {
                hits.add(claim);
            }
        }
        return hits;
    }
}
