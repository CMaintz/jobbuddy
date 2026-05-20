package com.autoapplicant.port.in.ai;

import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GenerateDocumentUseCase {

    CompletableFuture<StructuredDocument> generateDocument(
            UUID userId,
            String documentType,
            UUID jobId,
            String rawJobDescription,
            String templateId,
            String customInstructions,
            String targetLanguage,
            boolean showProfileImage,
            DocumentTheme theme);
}
