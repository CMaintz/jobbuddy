package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;

import java.util.UUID;

/**
 * Records what happened with an application — feedback received and lessons for
 * next time. Lessons feed back into future document generations.
 */
public interface UpdateOutcomeUseCase {
    Application updateOutcome(UUID applicationId, UUID userId, String outcomeFeedback, String outcomeLessons);
}
