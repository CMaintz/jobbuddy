package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.port.in.user.GetAiCareerProfileUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The profile the chat's model may read: the same identity-free projection generation uses, with
 * links stripped too, since nothing the chat shows should carry a URL of hers.
 */
@Service
public class AiCareerProfileService implements GetAiCareerProfileUseCase {

    private final CareerProfileContextService profiles;

    public AiCareerProfileService(CareerProfileContextService profiles) {
        this.profiles = profiles;
    }

    @Override
    public CareerProfileForAi getAiCareerProfile(UUID userId) {
        return profiles.build(userId).withoutLinks();
    }
}
