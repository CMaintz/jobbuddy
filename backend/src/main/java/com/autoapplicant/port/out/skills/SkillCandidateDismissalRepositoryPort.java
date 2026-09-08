package com.autoapplicant.port.out.skills;

import java.util.Set;
import java.util.UUID;

public interface SkillCandidateDismissalRepositoryPort {

    /** Normalized names this user has declined; never suggest them again. */
    Set<String> findDismissedNames(UUID userId);

    void dismiss(UUID userId, String normalizedName);
}
