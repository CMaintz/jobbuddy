package com.autoapplicant.domain.ai;

import java.util.UUID;

public record RefineDocumentRequest(
        UUID userId,
        String currentContent,
        String userMessage,
        String jobDescription,
        String targetLanguage
) {}
