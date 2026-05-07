package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;

import java.util.UUID;

public interface UpdateApplicationStatusUseCase {
    Application updateStatus(UUID applicationId, UUID userId, ApplicationStatus newStatus, String notes);
}
