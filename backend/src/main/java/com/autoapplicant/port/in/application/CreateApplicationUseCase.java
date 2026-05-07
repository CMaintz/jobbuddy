package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;

import java.util.UUID;

public interface CreateApplicationUseCase {
    Application createApplication(UUID userId, UUID jobId, UUID cvVersionId, String notes);
}
