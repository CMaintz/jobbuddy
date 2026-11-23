package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import java.util.UUID;

/**
 * Her career profile in the form that may be shown to an AI model: no name, contact details,
 * photo or links (see the privacy invariant in CLAUDE.md).
 */
public interface GetAiCareerProfileUseCase {

    CareerProfileForAi getAiCareerProfile(UUID userId);
}
