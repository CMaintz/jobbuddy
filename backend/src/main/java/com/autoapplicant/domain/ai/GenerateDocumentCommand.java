package com.autoapplicant.domain.ai;

import com.autoapplicant.domain.document.structured.DocumentTheme;
import java.util.UUID;

/**
 * Everything a request to generate an application document carries. A parameter object for
 * {@link com.autoapplicant.port.in.ai.GenerateDocumentUseCase}: the operation genuinely needs all of
 * these, but a twelve-argument method is one nobody can read or extend, and it forces every
 * collaborator in the pipeline to re-list the same arguments.
 */
public record GenerateDocumentCommand(
        UUID userId,
        String documentType,
        UUID jobId,
        String rawJobDescription,
        String templateId,
        UUID promptTemplateId,
        String customInstructions,
        String motivationText,
        String targetLanguage,
        boolean showProfileImage,
        DocumentTheme theme,
        String lengthPreference) {}
