package com.autoapplicant.port.out.application;

import com.autoapplicant.domain.application.ApplicationStatusEvent;

import java.util.List;
import java.util.UUID;

public interface ApplicationStatusEventRepositoryPort {
    ApplicationStatusEvent save(ApplicationStatusEvent event);
    /** All of a user's status events, oldest first — the input to funnel-velocity analytics. */
    List<ApplicationStatusEvent> findByUserId(UUID userId);

    /** One application's transitions, oldest first. Scoped by user: an id alone opens nothing. */
    List<ApplicationStatusEvent> findByApplicationIdAndUserId(UUID applicationId, UUID userId);
}
