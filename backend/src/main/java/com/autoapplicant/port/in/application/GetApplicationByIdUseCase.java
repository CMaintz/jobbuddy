package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;

import java.util.Optional;
import java.util.UUID;

public interface GetApplicationByIdUseCase {
    Optional<Application> getApplicationById(UUID id, UUID userId);
}
