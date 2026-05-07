package com.autoapplicant.adapter.web.dto.application;

import com.autoapplicant.domain.application.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateApplicationStatusRequest(
        @NotNull ApplicationStatus status,
        String notes
) {}
