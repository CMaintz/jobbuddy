package com.autoapplicant.domain.ai;

import java.util.UUID;

public record RefineDocumentRequest(
        UUID userId,
        String currentContent,
        String userMessage,
        String jobDescription,
        String targetLanguage,
        /** The CV section being refined (wire-key), or null for non-section refines. */
        String sectionKey
) {}
