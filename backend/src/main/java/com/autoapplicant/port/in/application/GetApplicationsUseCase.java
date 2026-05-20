package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface GetApplicationsUseCase {
    List<Application> getApplications(UUID userId);
    Page<Application> getApplications(UUID userId, Pageable pageable);
}
