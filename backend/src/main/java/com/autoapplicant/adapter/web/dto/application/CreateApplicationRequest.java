package com.autoapplicant.adapter.web.dto.application;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID jobId,
        UUID cvVersionId,
        String notes
) {}
