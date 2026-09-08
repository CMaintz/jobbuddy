package com.autoapplicant.domain.ai;

import java.util.UUID;

public record ReviewDocumentRequest(
        UUID userId,
        String currentContent,
        String documentType,
        String jobDescription,
        String targetLanguage
) {}
