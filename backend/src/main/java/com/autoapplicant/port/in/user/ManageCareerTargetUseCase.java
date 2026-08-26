package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.CareerStage;
import com.autoapplicant.domain.user.CareerTarget;

import java.util.List;
import java.util.UUID;

public interface ManageCareerTargetUseCase {
    /** The user's career target, or an empty default when none has been set. */
    CareerTarget get(UUID userId);

    CareerTarget upsert(UUID userId, List<String> targetArchetypes, String northStar,
                        String narrative, List<String> cultureRequirements, CareerStage careerStage);
}
