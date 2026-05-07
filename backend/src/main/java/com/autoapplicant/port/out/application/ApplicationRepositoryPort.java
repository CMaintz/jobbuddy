package com.autoapplicant.port.out.application;

import com.autoapplicant.domain.application.Application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepositoryPort {
    Application save(Application application);
    Optional<Application> findById(UUID id);
    List<Application> findByUserId(UUID userId);
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);
}
