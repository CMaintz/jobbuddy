package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.UUID;

public record PdfTemplate(UUID id, UUID userId, String name, String description,
                           String documentType, String htmlTemplate, String cssStyles,
                           boolean isSystem, boolean isActive, Instant createdAt) {}
