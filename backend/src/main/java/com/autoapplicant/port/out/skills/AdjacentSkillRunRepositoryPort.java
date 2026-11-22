package com.autoapplicant.port.out.skills;

import java.util.Optional;
import java.util.UUID;

/** Which skill set each user was last asked adjacent-skill questions about. */
public interface AdjacentSkillRunRepositoryPort {

    /** The fingerprint of the skill set last sent for suggestions; empty if never. */
    Optional<String> lastFingerprint(UUID userId);

    void record(UUID userId, String fingerprint);
}
