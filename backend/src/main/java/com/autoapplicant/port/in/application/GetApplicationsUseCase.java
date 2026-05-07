package com.autoapplicant.port.in.application;

import com.autoapplicant.domain.application.Application;

import java.util.List;
import java.util.UUID;

public interface GetApplicationsUseCase {
    List<Application> getApplications(UUID userId);
}
