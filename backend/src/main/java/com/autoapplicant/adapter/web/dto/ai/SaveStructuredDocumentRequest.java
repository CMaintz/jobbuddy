package com.autoapplicant.adapter.web.dto.ai;

import com.autoapplicant.domain.document.structured.StructuredDocument;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaveStructuredDocumentRequest(
        UUID jobId,
        @NotNull StructuredDocument document
) {}
