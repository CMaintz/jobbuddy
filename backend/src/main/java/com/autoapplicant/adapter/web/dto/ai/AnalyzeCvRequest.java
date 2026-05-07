package com.autoapplicant.adapter.web.dto.ai;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AnalyzeCvRequest(
        @NotNull UUID cvVersionId,
        UUID jobId
) {}
