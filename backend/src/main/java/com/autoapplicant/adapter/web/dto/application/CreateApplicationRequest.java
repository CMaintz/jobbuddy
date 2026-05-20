package com.autoapplicant.adapter.web.dto.application;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID jobId,
        UUID cvVersionId,
        UUID promptTemplateId,
        UUID generatedDocumentId,
        String status,
        String coverLetterText,
        String applicationText,
        String recruiterMessage,
        Integer matchScore,
        String notes
) {}
