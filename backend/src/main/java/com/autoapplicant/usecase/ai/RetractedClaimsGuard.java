package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.user.RetractedClaim;
import com.autoapplicant.port.out.user.RetractedClaimRepositoryPort;
import com.autoapplicant.usecase.common.KeywordMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Checks generated candidate-facing text against the user's retracted (disowned) claims,
 * so a claim the user has explicitly removed can never resurface in generated content.
 * Case-insensitive whole-token match via {@link KeywordMatcher}: a plain substring check would let a
 * short claim such as "Go" flag every "good", and in block mode that would fail generation.
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
        List<String> hits = new ArrayList<>();
        for (RetractedClaim rc : repo.findByUserId(userId)) {
            if (KeywordMatcher.contains(text, rc.claim())) {
                hits.add(rc.claim());
            }
        }
        return hits;
    }
}
