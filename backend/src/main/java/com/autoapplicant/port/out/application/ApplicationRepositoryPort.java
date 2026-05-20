package com.autoapplicant.port.out.application;

import com.autoapplicant.domain.application.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepositoryPort {
    Application save(Application application);
    Optional<Application> findById(UUID id);
    List<Application> findByUserId(UUID userId);
    Page<Application> findByUserId(UUID userId, Pageable pageable);
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);
}
