package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.CvSection;
import com.autoapplicant.domain.document.CvSectionPrompts;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CvSectionPromptsRepositoryPort {

    Optional<CvSectionPrompts> findByUserId(UUID userId);

    /** Upsert the user's section prompts (one row per user); blank entries are dropped. */
    CvSectionPrompts save(UUID userId, Map<CvSection, String> prompts);
}
