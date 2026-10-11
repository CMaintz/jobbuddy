package com.autoapplicant.domain.document;

import java.time.Instant;
import java.util.UUID;

public record GeneratedDocument(
        UUID id,
        UUID userId,
        UUID applicationId,
        UUID jobId,
        DocumentType documentType,
        String content,
        String structuredContent,
        String templateId,
        String exportMode,
        UUID promptTemplateId,
        UUID cvVersionId,
        String modelUsed,
        Integer tokensUsed,
        Instant createdAt
) {
    public GeneratedDocument withApplicationId(UUID applicationId) {
        return new GeneratedDocument(id, userId, applicationId, jobId, documentType, content,
                structuredContent, templateId, exportMode, promptTemplateId, cvVersionId, modelUsed,
                tokensUsed, createdAt);
    }
}
