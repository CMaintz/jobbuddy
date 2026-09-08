package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;

import java.util.UUID;

public interface GenerateTailoredCvUseCase {
    StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                          String customInstructions, String targetLanguage,
                                          String templateId, UUID promptTemplateId,
                                          boolean showProfileImage, DocumentTheme theme,
                                          String lengthPreference);
}
